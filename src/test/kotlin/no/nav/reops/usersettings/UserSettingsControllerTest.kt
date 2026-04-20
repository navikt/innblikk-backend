package no.nav.reops.usersettings

import no.nav.reops.ControlAdvice
import no.nav.reops.security.AuthenticatedUser
import no.nav.reops.security.AuthenticatedUserProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.put

@WebMvcTest(
    controllers = [UserSettingsController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, OAuth2ResourceServerAutoConfiguration::class],
)
@Import(UserSettingsControllerTest.MockConfig::class, ControlAdvice::class)
class UserSettingsControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userSettingsService: UserSettingsService,
    @Autowired private val authenticatedUserProvider: AuthenticatedUserProvider,
) {

    @TestConfiguration
    class MockConfig {
        @Bean
        fun userSettingsService(): UserSettingsService = Mockito.mock(UserSettingsService::class.java)

        @Bean
        fun authenticatedUserProvider(): AuthenticatedUserProvider = Mockito.mock(AuthenticatedUserProvider::class.java)
    }

    private val basePath = "/api/user-settings"

    private val validRequestBody = """
        {
          "settings": {"beta_opt_in": "true", "grafbygger_always_show_sql": "false"},
          "analyticsId": "analytics-uuid-123"
        }
    """.trimIndent()

    @Test
    fun `PUT returns 200 with response body on success`() {
        val response = UpsertUserSettingsResponse(navIdent = "Z123456", settingsCount = 2)
        whenever(authenticatedUserProvider.getCurrentUser())
            .thenReturn(AuthenticatedUser(email = "test@nav.no", name = "Test User", navIdent = "Z123456"))
        whenever(userSettingsService.upsertSettings(any(), any())).thenReturn(response)

        val result = mockMvc.put(basePath) {
            contentType = MediaType.APPLICATION_JSON
            content = validRequestBody
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"navIdent\":\"Z123456\"")
        assertThat(result.response.contentAsString).contains("\"settingsCount\":2")
    }

    @Test
    fun `PUT returns 401 when user is not authenticated`() {
        whenever(authenticatedUserProvider.getCurrentUser()).thenReturn(null)

        val result = mockMvc.put(basePath) {
            contentType = MediaType.APPLICATION_JSON
            content = validRequestBody
        }.andReturn()

        assertThat(result.response.status).isEqualTo(401)
    }

    @Test
    fun `PUT returns 401 when navIdent is null`() {
        whenever(authenticatedUserProvider.getCurrentUser())
            .thenReturn(AuthenticatedUser(email = "test@nav.no", name = "Test User", navIdent = null))

        val result = mockMvc.put(basePath) {
            contentType = MediaType.APPLICATION_JSON
            content = validRequestBody
        }.andReturn()

        assertThat(result.response.status).isEqualTo(401)
    }

    @Test
    fun `PUT handles empty settings map`() {
        val response = UpsertUserSettingsResponse(navIdent = "Z123456", settingsCount = 0)
        whenever(authenticatedUserProvider.getCurrentUser())
            .thenReturn(AuthenticatedUser(email = "test@nav.no", name = "Test User", navIdent = "Z123456"))
        whenever(userSettingsService.upsertSettings(any(), any())).thenReturn(response)

        val result = mockMvc.put(basePath) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"settings": {}, "analyticsId": null}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"settingsCount\":0")
    }

    @Test
    fun `PUT handles null analyticsId`() {
        val response = UpsertUserSettingsResponse(navIdent = "Z123456", settingsCount = 1)
        whenever(authenticatedUserProvider.getCurrentUser())
            .thenReturn(AuthenticatedUser(email = "test@nav.no", name = "Test User", navIdent = "Z123456"))
        whenever(userSettingsService.upsertSettings(any(), any())).thenReturn(response)

        val result = mockMvc.put(basePath) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"settings": {"beta_opt_in": "true"}, "analyticsId": null}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
    }
}
