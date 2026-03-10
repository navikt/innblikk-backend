package no.nav.reops.dashboard

import java.time.LocalDateTime

data class DashboardDto(
    val id: Long,
    val projectId: Long,
    val name: String,
    val description: String?,
    val updatedAt: LocalDateTime,
)

data class CreateDashboardRequest(
    val name: String,
    val description: String?,
)

data class UpdateDashboardRequest(
    val name: String,
    val description: String?,
    val projectId: Long? = null,
)
