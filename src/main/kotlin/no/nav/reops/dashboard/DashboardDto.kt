package no.nav.reops.dashboard

import java.time.LocalDateTime

data class DashboardDto(
    val id: Long,
    val projectId: Long,
    val name: String,
    val description: String?,
    val updatedAt: LocalDateTime,
    val changedByEmail: String? = null,
    val changedByName: String? = null,
    val changedByNavIdent: String? = null,
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
