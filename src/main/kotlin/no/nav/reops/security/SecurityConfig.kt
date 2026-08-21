package no.nav.reops.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.config.annotation.web.builders.HttpSecurity
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

@Configuration
class SecurityConfig(
    private val localDevTokenAuthFilter: LocalDevTokenAuthFilter,
) {

    private val pathMatcher = AntPathMatcher()

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
                        val authorities = authentication.get().authorities
                        val isLocalDevScope = authorities.any { it.authority == LOCAL_DEV_AUTHORITY }

                        val granted = if (isLocalDevScope) {
                            LOCAL_DEV_ALLOWED_PATTERNS.any { pattern ->
                                pathMatcher.match(pattern, context.request.requestURI)
                            }
                        } else {
                            authentication.get().isAuthenticated
                        }

                        AuthorizationDecision(granted)
                    }
            }.oauth2ResourceServer { rs ->
                rs.jwt { }
            }

        return http.build()
    }
}