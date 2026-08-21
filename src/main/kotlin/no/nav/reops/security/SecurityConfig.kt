package no.nav.reops.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationTrustResolver
import org.springframework.security.authentication.AuthenticationTrustResolverImpl
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.util.AntPathMatcher

/**
 * Endpoint patterns reachable by a request authenticated via [LOCAL_DEV_AUTHORITY] (the
 * dev-only static-token path — see [LocalDevTokenAuthFilter]). Deliberately a narrow subset
 * covering only what's needed to render/edit projects/dashboards/graphs/queries for design
 * purposes — NOT the full API a real Azure AD-authenticated user gets. Notably excludes
 * `/api/cohort` and everything under it (includes hard-delete/permanent-delete operations).
 */
private val LOCAL_DEV_ALLOWED_PATTERNS = listOf(
    "/api/projects/**",
    "/api/sidegroup/**",
    "/api/canvas/**",
    "/api/user-settings/**",
)

private val pathMatcher = AntPathMatcher()

// AnonymousAuthenticationToken (the principal Spring assigns to a request with NO
// credentials at all, when anonymous auth isn't explicitly disabled) reports
// isAuthenticated() == true — that's real, documented Spring Security behavior, not a
// typo. A naive `authentication.isAuthenticated` check therefore treats a fully
// unauthenticated request the same as a real logged-in user. Spring's own
// AuthenticatedAuthorizationManager.authenticated() avoids this by also checking
// AuthenticationTrustResolver.isAnonymous(...) — replicate that check explicitly below
// since this endpoint uses a custom access-decision function instead of `.authenticated()`.
private val trustResolver: AuthenticationTrustResolver = AuthenticationTrustResolverImpl()

/**
 * The actual authorization decision for every request not covered by the `permitAll()`
 * allowlist in [SecurityConfig.securityFilterChain]. Extracted to a top-level, dependency-free
 * function (no Spring context needed) specifically so it has direct unit test coverage —
 * see `SecurityConfigTest` — without needing a full `MockMvc`/Spring Security test setup.
 */
internal fun decideAccess(authentication: Authentication, requestUri: String): Boolean {
    val isRealAuthentication = authentication.isAuthenticated && !trustResolver.isAnonymous(authentication)
    val isLocalDevScope = isRealAuthentication && authentication.authorities.any { it.authority == LOCAL_DEV_AUTHORITY }

    return if (isLocalDevScope) {
        LOCAL_DEV_ALLOWED_PATTERNS.any { pattern -> pathMatcher.match(pattern, requestUri) }
    } else {
        isRealAuthentication
    }
}

@Configuration
class SecurityConfig(
    private val localDevTokenAuthFilter: LocalDevTokenAuthFilter,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }
            .addFilterBefore(localDevTokenAuthFilter, BearerTokenAuthenticationFilter::class.java)
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("/api/swagger-ui/**").permitAll()
                    .requestMatchers("/api/openapi/**").permitAll()
                    .requestMatchers("/internal/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/stats").permitAll()
                    .requestMatchers("/api/canvas/ws").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .anyRequest().access { authentication, context ->
                        AuthorizationDecision(decideAccess(authentication.get(), context.request.requestURI))
                    }
            }.oauth2ResourceServer { rs ->
                rs.jwt { }
            }

        return http.build()
    }
}