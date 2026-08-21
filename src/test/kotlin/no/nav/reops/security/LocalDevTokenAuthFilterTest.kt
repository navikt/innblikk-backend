package no.nav.reops.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

private const val REAL_TOKEN = "correct-static-dev-token"

@ExtendWith(MockitoExtension::class)
class LocalDevTokenAuthFilterTest {

    @Mock
    private lateinit var request: HttpServletRequest

    @Mock
    private lateinit var response: HttpServletResponse

    @Mock
    private lateinit var filterChain: FilterChain

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    private fun filterWithEnv(env: Map<String, String?>): LocalDevTokenAuthFilter =
        LocalDevTokenAuthFilter(envLookup = { key -> env[key] })

    @Test
    fun `does nothing when DEV_LOCAL_AUTH_TOKEN is not configured`() {
        val filter = filterWithEnv(mapOf("DEV_LOCAL_AUTH_TOKEN" to null))

        filter.doFilter(request, response, filterChain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `does nothing when DEV_LOCAL_AUTH_TOKEN is blank`() {
        val filter = filterWithEnv(mapOf("DEV_LOCAL_AUTH_TOKEN" to "   "))

        filter.doFilter(request, response, filterChain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `refuses to activate when NAIS_CLUSTER_NAME contains prod, even with a valid token`() {
        val filter = filterWithEnv(
            mapOf("DEV_LOCAL_AUTH_TOKEN" to REAL_TOKEN, "NAIS_CLUSTER_NAME" to "prod-gcp"),
        )

        filter.doFilter(request, response, filterChain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `refuses when NAIS_CLUSTER_NAME contains prod case-insensitively`() {
        val filter = filterWithEnv(
            mapOf("DEV_LOCAL_AUTH_TOKEN" to REAL_TOKEN, "NAIS_CLUSTER_NAME" to "PROD-GCP"),
        )

        filter.doFilter(request, response, filterChain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun `does nothing when no Authorization header is present`() {
        val filter = filterWithEnv(mapOf("DEV_LOCAL_AUTH_TOKEN" to REAL_TOKEN, "NAIS_CLUSTER_NAME" to "dev-gcp"))
        whenever(request.getHeader("Authorization")).thenReturn(null)

        filter.doFilter(request, response, filterChain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `does nothing when the presented token is wrong`() {
        val filter = filterWithEnv(mapOf("DEV_LOCAL_AUTH_TOKEN" to REAL_TOKEN, "NAIS_CLUSTER_NAME" to "dev-gcp"))
        whenever(request.getHeader("Authorization")).thenReturn("Bearer wrong-token")

        filter.doFilter(request, response, filterChain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun `does NOT authenticate a correct token missing the Bearer scheme (regression test)`() {
        // Regression test: Kotlin's removePrefix() used to silently accept a bare
        // "Authorization: <token>" header (no "Bearer " scheme) as valid, since removePrefix()
        // returns the string unchanged when the prefix isn't present. Sending the raw correct
        // token value with no scheme at all must NOT authenticate.
        val filter = filterWithEnv(mapOf("DEV_LOCAL_AUTH_TOKEN" to REAL_TOKEN, "NAIS_CLUSTER_NAME" to "dev-gcp"))
        whenever(request.getHeader("Authorization")).thenReturn(REAL_TOKEN)

        filter.doFilter(request, response, filterChain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `authenticates with the correct Bearer-scheme token, using a fixed non-forgeable identity`() {
        val filter = filterWithEnv(mapOf("DEV_LOCAL_AUTH_TOKEN" to REAL_TOKEN, "NAIS_CLUSTER_NAME" to "dev-gcp"))
        whenever(request.getHeader("Authorization")).thenReturn("Bearer $REAL_TOKEN")
        whenever(request.getHeader("X-Dev-Nav-Ident")).thenReturn("Z999999")
        whenever(request.method).thenReturn("GET")
        whenever(request.requestURI).thenReturn("/api/projects")

        filter.doFilter(request, response, filterChain)

        val authentication = SecurityContextHolder.getContext().authentication
        assertThat(authentication).isInstanceOf(JwtAuthenticationToken::class.java)
        val jwtAuth = authentication as JwtAuthenticationToken
        assertThat(jwtAuth.authorities.map { it.authority }).containsExactly(LOCAL_DEV_AUTHORITY)
        // The identity claims are ALWAYS fixed, regardless of the attacker-controllable
        // X-Dev-Nav-Ident header supplied above — that header must never end up as the
        // NAVident/preferred_username claim (see the class doc + security-audit note).
        assertThat(jwtAuth.token.getClaimAsString("NAVident")).isEqualTo("LOCAL_DEV_SHARED")
        assertThat(jwtAuth.token.getClaimAsString("preferred_username")).isEqualTo("local-dev@local.dev")

        val requestCaptor = argumentCaptor<HttpServletRequest>()
        verify(filterChain).doFilter(requestCaptor.capture(), any())
        // Authorization header must be stripped from what's passed further down the chain, so
        // Spring's own JWT resource-server filter doesn't also try (and fail) to parse the
        // static token as a real JWT.
        assertThat(requestCaptor.firstValue.getHeader("Authorization")).isNull()
    }

    @Test
    fun `accepts the Bearer scheme case-insensitively per RFC 7235`() {
        val filter = filterWithEnv(mapOf("DEV_LOCAL_AUTH_TOKEN" to REAL_TOKEN, "NAIS_CLUSTER_NAME" to "dev-gcp"))
        whenever(request.getHeader("Authorization")).thenReturn("bearer $REAL_TOKEN")
        whenever(request.method).thenReturn("GET")
        whenever(request.requestURI).thenReturn("/api/projects")

        filter.doFilter(request, response, filterChain)

        assertThat(SecurityContextHolder.getContext().authentication).isInstanceOf(JwtAuthenticationToken::class.java)
    }

    @Test
    fun `falls back to a placeholder display name when no X-Dev-Nav-Ident header is supplied`() {
        val filter = filterWithEnv(mapOf("DEV_LOCAL_AUTH_TOKEN" to REAL_TOKEN, "NAIS_CLUSTER_NAME" to "dev-gcp"))
        whenever(request.getHeader("Authorization")).thenReturn("Bearer $REAL_TOKEN")
        whenever(request.getHeader("X-Dev-Nav-Ident")).thenReturn(null)
        whenever(request.method).thenReturn("GET")
        whenever(request.requestURI).thenReturn("/api/projects")

        filter.doFilter(request, response, filterChain)

        val jwtAuth = SecurityContextHolder.getContext().authentication as JwtAuthenticationToken
        assertThat(jwtAuth.token.getClaimAsString("name")).isEqualTo("Local dev")
    }
}
