package no.nav.reops.usersettings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

@ExtendWith(MockitoExtension::class)
class UserSettingsServiceTest {

    @Mock
    private lateinit var userSettingsRepository: UserSettingsRepository

    @Mock
    private lateinit var userSettingValueRepository: UserSettingValueRepository

    private lateinit var userSettingsService: UserSettingsService

    @BeforeEach
    fun setUp() {
        userSettingsService = UserSettingsService(userSettingsRepository, userSettingValueRepository)
    }

    @Test
    fun `upsertSettings upserts user and all setting values`() {
        val request = UpsertUserSettingsRequest(
            settings = mapOf("beta_opt_in" to "true", "grafbygger_always_show_sql" to "false"),
            analyticsId = "analytics-uuid-123",
        )

        val result = userSettingsService.upsertSettings("Z123456", request)

        verify(userSettingsRepository).upsertUser("Z123456")
        verify(userSettingValueRepository).upsertValue(
            navIdent = "Z123456",
            key = "beta_opt_in",
            value = "true",
            analyticsId = "analytics-uuid-123",
        )
        verify(userSettingValueRepository).upsertValue(
            navIdent = "Z123456",
            key = "grafbygger_always_show_sql",
            value = "false",
            analyticsId = "analytics-uuid-123",
        )
        assertThat(result.navIdent).isEqualTo("Z123456")
        assertThat(result.settingsCount).isEqualTo(2)
    }

    @Test
    fun `upsertSettings handles empty settings map`() {
        val request = UpsertUserSettingsRequest(
            settings = emptyMap(),
            analyticsId = null,
        )

        val result = userSettingsService.upsertSettings("Z123456", request)

        verify(userSettingsRepository).upsertUser("Z123456")
        verifyNoMoreInteractions(userSettingValueRepository)
        assertThat(result.settingsCount).isEqualTo(0)
    }

    @Test
    fun `upsertSettings handles null analyticsId`() {
        val request = UpsertUserSettingsRequest(
            settings = mapOf("beta_opt_in" to "true"),
            analyticsId = null,
        )

        val result = userSettingsService.upsertSettings("T654321", request)

        verify(userSettingsRepository).upsertUser("T654321")
        verify(userSettingValueRepository).upsertValue(
            navIdent = "T654321",
            key = "beta_opt_in",
            value = "true",
            analyticsId = null,
        )
        assertThat(result.navIdent).isEqualTo("T654321")
        assertThat(result.settingsCount).isEqualTo(1)
    }
}
