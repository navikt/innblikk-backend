package no.nav.reops.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest
import java.time.Instant
import java.util.Collections
import java.util.Enumeration

/**
 * The authority granted to a request authenticated via [LocalDevTokenAuthFilter], instead of a
 * real Azure AD JWT. Used elsewhere (see [SecurityConfig]) to scope this principal down to a
 * safe subset of endpoints — never the full API surface a real user gets.
 */
const val LOCAL_DEV_AUTHORITY = "SCOPE_local-dev"

/**
 * Fixed, non-forgeable identity used for every request authenticated via the static dev token.
 * Deliberately NOT taken from client input — see the security note on `X-Dev-Nav-Ident` below.
 * Everyone using the shared token shares this one identity for authorization/data-scoping
 * purposes (one shared user-settings row, one shared canvas presence identity, one shared
 * audit-trail attribution). That's an intentional consequence of it being a *shared* token.
 */
private const val LOCAL_DEV_SENTINEL_IDENT = "LOCAL_DEV_SHARED"

/**
 * DEV-ONLY escape hatch: lets a locally-run frontend (no Azure AD sidecar available on a
 * developer/designer's laptop) authenticate to this REAL deployed dev backend using a single
 * shared, revocable, dev-only bearer token instead of a real Azure AD JWT.
 *
 * Why this exists: contributors without naisdevice (e.g. a designer) can reach this app's dev
 * ingress (innblikk-backend.ansatt.dev.nav.no) over the ansatt network, but their local frontend
 * process has no Azure AD app registration and therefore cannot obtain a real user JWT or do an
 * OBO exchange. See innblikk-frontend's README ("Bane A" / path A).
 *
 * Safety, deliberately layered — any ONE of these failing closes the hole:
 *
 * 1. Requires the `DEV_LOCAL_AUTH_TOKEN` env var to be set AND non-blank. This env var must
 *    NEVER be set in nais-prod.yaml — it is the primary gate, and must stay entirely absent
 *    from the prod manifest (not "set to empty" — actually absent).
 * 2. Refuses to activate if `NAIS_CLUSTER_NAME` contains "prod", regardless of whether the
 *    token env var is somehow present — defense in depth against manifest mistakes/copy-paste.
 * 3. Constant-time comparison ([MessageDigest.isEqual]) against the configured token, not a
 *    plain `==`, to avoid timing side-channels leaking the token byte-by-byte.
 * 4. Deliberately NOT gated via `@ConditionalOnProperty`/`@Profile` — this app is AOT-compiled
 *    to a native image with the "kubernetes" Spring profile forced active at build time (see
 *    application.yaml's comment on `jwk-set-uri`), meaning Spring conditional bean registration
 *    is frozen into the binary at build time and would be IDENTICAL for the dev and prod
 *    deployment of the same built artifact. All dev/prod differentiation here therefore has to
 *    happen at REQUEST TIME via plain `System.getenv` reads inside always-registered code, not
 *    via any Spring conditional mechanism.
 * 5. Grants only [LOCAL_DEV_AUTHORITY] — [SecurityConfig] restricts this authority to a narrow
 *    allowlist of endpoints (project/dashboard/graph/query/canvas/user-settings CRUD needed to
 *    render the app), never the full API. Notably excludes destructive/bulk operations (e.g.
 *    cohort hard-delete).
 * 6. Any endpoint that accepts free-form SQL text from the client (query create/update) treats
 *    it as untrusted input regardless of which auth path was used — see
 *    `no.nav.reops.query.SqlTextValidator`. Note this backend never executes that SQL against
 *    BigQuery itself (it only stores query *definitions* in Postgres for the frontend to run
 *    later) — actual BigQuery execution, cost estimation, and the same read-only guard live in
 *    innblikk-frontend's `/api/bigquery` routes. There is nothing to "cost estimate" here.
 *
 * The resulting principal's identity (`NAVident`, `preferred_username` claims) is ALWAYS
 * [LOCAL_DEV_SENTINEL_IDENT] — a fixed, non-forgeable value, never taken from client input.
 *
 * Security-audit finding fixed here (previously): the `X-Dev-Nav-Ident` header was used
 * verbatim as the `NAVident` claim, which meant it wasn't just a cosmetic log label as
 * documented — `UserSettingsController`/`UserSettingsService` key rows by `navIdent`, and
 * `ChangeService` stamps every audit-trail row's `changedByNavIdent`/`changedByEmail` from the
 * same claim. A token holder could therefore read/overwrite ANY other user's settings row, or
 * forge audit-trail attribution to an arbitrary NAVident, just by setting that header — real
 * cross-tenant authorization bypass, not just log spoofing.
 *
 * The header is still read and logged (see below) purely for human-readable correlation in log
 * search — e.g. "who on the shared token was probably making this request" — but it can never
 * again be used to scope data access or forge attribution, because it no longer becomes any
 * claim the rest of the app treats as an identity.
 */
@Component
class LocalDevTokenAuthFilter : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(LocalDevTokenAuthFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val configuredToken = System.getenv("DEV_LOCAL_AUTH_TOKEN")
        val clusterName = System.getenv("NAIS_CLUSTER_NAME") ?: ""
        val isProdCluster = clusterName.contains("prod", ignoreCase = true)

        if (configuredToken.isNullOrBlank() || isProdCluster) {
            filterChain.doFilter(request, response)
            return
        }

        val presentedToken = request.getHeader("Authorization")?.removePrefix("Bearer ")?.trim()

        if (presentedToken != null && constantTimeEquals(presentedToken, configuredToken)) {
            // Purely a human-readable log label — NEVER used as an authorization/claim value.
            // See the class doc above for why that distinction matters.
            val requestedLabel = request.getHeader("X-Dev-Nav-Ident")?.trim()?.takeIf { it.isNotBlank() }
            logger.info(
                "Local-dev static-token auth used for {} {} (requested label: {})",
                request.method,
                request.requestURI,
                requestedLabel ?: "(none)",
            )

            val displayName = if (requestedLabel != null) "Local dev ($requestedLabel)" else "Local dev"

            val fakeJwt = Jwt.withTokenValue("local-dev-static-token")
                .header("alg", "none")
                .claim("NAVident", LOCAL_DEV_SENTINEL_IDENT)
                .claim("preferred_username", "local-dev@local.dev")
                .claim("name", displayName)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build()

            SecurityContextHolder.getContext().authentication =
                JwtAuthenticationToken(fakeJwt, listOf(SimpleGrantedAuthority(LOCAL_DEV_AUTHORITY)))

            // Strip the Authorization header before continuing the chain so Spring Security's
            // own oauth2ResourceServer JWT filter (further down the chain) doesn't also try to
            // parse "Bearer <static-token>" as a real JWT and reject/overwrite the
            // authentication we just set above.
            filterChain.doFilter(AuthorizationHeaderStrippedRequest(request), response)
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}

private class AuthorizationHeaderStrippedRequest(
    request: HttpServletRequest,
) : HttpServletRequestWrapper(request) {

    private fun isAuthorizationHeader(name: String) = name.equals("Authorization", ignoreCase = true)

    override fun getHeader(name: String): String? =
        if (isAuthorizationHeader(name)) null else super.getHeader(name)

    override fun getHeaders(name: String): Enumeration<String> =
        if (isAuthorizationHeader(name)) Collections.emptyEnumeration() else super.getHeaders(name)

    override fun getHeaderNames(): Enumeration<String> {
        val filtered = super.getHeaderNames().toList().filterNot { isAuthorizationHeader(it) }
        return Collections.enumeration(filtered)
    }
}
