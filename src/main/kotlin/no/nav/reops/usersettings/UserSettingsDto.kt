package no.nav.reops.usersettings

data class UpsertUserSettingsRequest(
    val settings: Map<String, String>,
    val analyticsId: String?,
)

data class UpsertUserSettingsResponse(
    val navIdent: String,
    val settingsCount: Int,
)
