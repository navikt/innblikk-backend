package no.nav.reops.graphcategory

import java.time.LocalDateTime

data class GraphCategoryDto(
    val id: Long,
    val dashboardId: Long,
    val name: String,
    val ordering: Int,
    val updatedAt: LocalDateTime,
)

data class CreateGraphCategoryRequest(
    val name: String,
)

data class UpdateGraphCategoryRequest(
    val name: String,
)

data class GraphCategoryOrderingEntry(
    val id: Long,
    val ordering: Int,
)

