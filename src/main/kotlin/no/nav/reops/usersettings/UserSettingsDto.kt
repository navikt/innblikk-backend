package no.nav.reops.usersettings

data class UpsertUserSettingsRequest(
    val settings: Map<String, String>,
)

data class UpsertUserSettingsResponse(
    val navIdent: String,
    val settingsCount: Int,
)

data class GetUserSettingsResponse(
    val settings: Map<String, String>,
)
