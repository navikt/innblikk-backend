package no.nav.reops.usersettings

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserSettingsService(
    private val userSettingsRepository: UserSettingsRepository,
    private val userSettingValueRepository: UserSettingValueRepository,
) {
    fun getSettings(navIdent: String): GetUserSettingsResponse {
        val values = userSettingValueRepository.findAllByNavIdent(navIdent)
        return GetUserSettingsResponse(settings = values.associate { it.settingKey to it.value })
    }

    @Transactional
    fun upsertSettings(
        navIdent: String,
        request: UpsertUserSettingsRequest,
    ): UpsertUserSettingsResponse {
        userSettingsRepository.upsertUser(navIdent)
        request.settings.forEach { (key, value) ->
            userSettingValueRepository.upsertValue(
                navIdent = navIdent,
                key = key,
                value = value,
            )
        }
        return UpsertUserSettingsResponse(
            navIdent = navIdent,
            settingsCount = request.settings.size,
        )
    }
}
