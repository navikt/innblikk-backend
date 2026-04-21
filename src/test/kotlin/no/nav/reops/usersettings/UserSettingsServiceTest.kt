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
        )

        val result = userSettingsService.upsertSettings("Z123456", request)

        verify(userSettingsRepository).upsertUser("Z123456")
        verify(userSettingValueRepository).upsertValue(
            navIdent = "Z123456",
            key = "beta_opt_in",
            value = "true",
        )
        verify(userSettingValueRepository).upsertValue(
            navIdent = "Z123456",
            key = "grafbygger_always_show_sql",
            value = "false",
        )
        assertThat(result.navIdent).isEqualTo("Z123456")
        assertThat(result.settingsCount).isEqualTo(2)
    }

    @Test
    fun `upsertSettings handles empty settings map`() {
        val request = UpsertUserSettingsRequest(
            settings = emptyMap(),
        )

        val result = userSettingsService.upsertSettings("Z123456", request)

        verify(userSettingsRepository).upsertUser("Z123456")
        verifyNoMoreInteractions(userSettingValueRepository)
        assertThat(result.settingsCount).isEqualTo(0)
    }
}
