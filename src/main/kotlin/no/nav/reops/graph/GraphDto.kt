package no.nav.reops.graph

import java.time.LocalDateTime

data class GraphDto(
    val id: Long,
    val dashboardId: Long,
    val name: String,
    val graphType: String?,
    val updatedAt: LocalDateTime,
)

data class CreateGraphRequest(
    val name: String,
    val graphType: String?,
)

data class UpdateGraphRequest(
    val name: String,
    val graphType: String?,
)
