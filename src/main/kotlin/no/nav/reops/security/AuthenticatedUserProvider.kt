package no.nav.reops.security

import org.slf4j.LoggerFactory
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

    private val logger = LoggerFactory.getLogger(AuthenticatedUserProvider::class.java)

    fun getCurrentUser(): AuthenticatedUser? {
        val authentication = SecurityContextHolder.getContext().authentication ?: return null
        if (authentication !is JwtAuthenticationToken) return null

        val claims = authentication.token.claims
        logger.info("JWT claims: {}", claims)
        logger.info("NAVident claim: {}", claims["NAVident"])
        return AuthenticatedUser(
            email = claims["preferred_username"] as? String,
            name = claims["name"] as? String,
            navIdent = claims["NAVident"] as? String,
        )
    }
}

