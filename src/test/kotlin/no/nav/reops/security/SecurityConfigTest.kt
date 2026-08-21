package no.nav.reops.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

private fun realJwtAuthentication(vararg authorities: String) = JwtAuthenticationToken(
    Jwt.withTokenValue("token")
        .header("alg", "none")
        .claim("NAVident", "Z123456")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .build(),
    authorities.map { SimpleGrantedAuthority(it) },
)

class SecurityConfigTest {

    @ParameterizedTest
    @ValueSource(strings = ["/api/projects", "/api/cohort", "/api/user-settings", "/api/anything-at-all"])
    fun `denies a fully anonymous request (no credentials at all) on every protected path`(path: String) {
        // Regression test: AnonymousAuthenticationToken.isAuthenticated() returns true (real
        // Spring Security behavior) - a naive isAuthenticated check would incorrectly grant
        // access to a request with zero credentials. This must always be denied.
        val anonymous = AnonymousAuthenticationToken(
            "key",
            "anonymousUser",
            listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")),
        )
        assertThat(anonymous.isAuthenticated).isTrue() // sanity-check the premise of this test

        assertThat(decideAccess(anonymous, path)).isFalse()
    }

    @Test
    fun `denies an authentication that is explicitly not authenticated`() {
        val unauthenticated = UsernamePasswordAuthenticationToken.unauthenticated("user", "credentials")
        assertThat(decideAccess(unauthenticated, "/api/projects")).isFalse()
    }

    @Test
    fun `grants a real authenticated user (non local-dev, non anonymous) access to any path`() {
        val real = realJwtAuthentication("SCOPE_default")
        assertThat(decideAccess(real, "/api/projects")).isTrue()
        assertThat(decideAccess(real, "/api/cohort/5/permanent")).isTrue()
        assertThat(decideAccess(real, "/api/anything-at-all")).isTrue()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/api/projects",
            "/api/projects/1/dashboards",
            "/api/projects/1/dashboards/2/categories/3/graphs/4/queries",
            "/api/sidegroup",
            "/api/sidegroup/1",
            "/api/canvas/ws-ticket",
            "/api/user-settings",
        ],
    )
    fun `grants local-dev scope access to allowlisted paths`(path: String) {
        val localDev = realJwtAuthentication(LOCAL_DEV_AUTHORITY)
        assertThat(decideAccess(localDev, path)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/api/cohort",
            "/api/cohort/5",
            "/api/cohort/5/permanent",
            "/api/stats",
            "/internal/health",
            "/api/openapi",
        ],
    )
    fun `denies local-dev scope access to anything outside the allowlist`(path: String) {
        val localDev = realJwtAuthentication(LOCAL_DEV_AUTHORITY)
        assertThat(decideAccess(localDev, path)).isFalse()
    }

    @Test
    fun `an anonymous token can never be mistaken for local-dev scope even with a matching authority name`() {
        // Defense in depth: even if something upstream ever attached a LOCAL_DEV_AUTHORITY-named
        // authority to an anonymous token, isRealAuthentication must still gate it off first.
        val anonymousWithSuspiciousAuthority = AnonymousAuthenticationToken(
            "key",
            "anonymousUser",
            listOf(SimpleGrantedAuthority(LOCAL_DEV_AUTHORITY)),
        )
        assertThat(decideAccess(anonymousWithSuspiciousAuthority, "/api/projects")).isFalse()
    }

    @Test
    fun `an authenticated-but-untrusted TestingAuthenticationToken with no matching authorities is denied local-dev paths but allowed as a real user`() {
        val genericAuthenticated = TestingAuthenticationToken("someone", "credentials", "SCOPE_default")
        genericAuthenticated.isAuthenticated = true
        assertThat(decideAccess(genericAuthenticated, "/api/projects")).isTrue()
        assertThat(decideAccess(genericAuthenticated, "/api/cohort/5/permanent")).isTrue()
    }
}
