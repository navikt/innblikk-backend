package no.nav.reops.graph

import java.time.LocalDateTime

data class GraphDto(
    val id: Long,
    val categoryId: Long,
    val name: String,
    val description: String?,
    val graphType: GraphType?,
    val width: Int,
    val ordering: Int,
    val updatedAt: LocalDateTime,
    val changedByEmail: String? = null,
    val changedByName: String? = null,
    val changedByNavIdent: String? = null,
)

data class CreateGraphRequest(
    val name: String,
    val description: String? = null,
    val graphType: GraphType?,
    val width: Int? = null,
)

data class UpdateGraphRequest(
    val name: String,
    val description: String? = null,
    val graphType: GraphType?,
    val width: Int? = null,
)

data class GraphOrderingEntry(
    val id: Long,
    val ordering: Int,
)

