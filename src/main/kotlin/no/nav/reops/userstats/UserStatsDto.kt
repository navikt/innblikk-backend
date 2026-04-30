package no.nav.reops.userstats

data class UserStatsResponse(
    val totalUsers: Long,
    val activeUsers: Long,
    val activeUserWindowDays: Int,
    val settings: Map<String, Map<String, Long>>,
)
