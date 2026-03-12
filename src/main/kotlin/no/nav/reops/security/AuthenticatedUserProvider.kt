package no.nav.reops.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

data class AuthenticatedUser(
    val email: String?,
    val name: String?,
    val navIdent: String?,
)

@Component
class AuthenticatedUserProvider {
    fun getCurrentUser(): AuthenticatedUser? {
        val authentication = SecurityContextHolder.getContext().authentication ?: return null
        if (authentication !is JwtAuthenticationToken) return null

        val claims = authentication.token.claims
        return AuthenticatedUser(
            email = claims["preferred_username"] as? String,
            name = claims["name"] as? String,
            navIdent = claims["NAVident"] as? String,
        )
    }
}

