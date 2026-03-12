package no.nav.reops.project

import java.time.LocalDateTime

data class ProjectDto(
    val id: Long,
    val name: String,
    val description: String?,
    val updatedAt: LocalDateTime,
    val changedByEmail: String? = null,
    val changedByName: String? = null,
    val changedByNavIdent: String? = null,
)

data class CreateProjectRequest(
    val name: String,
    val description: String?,
)

data class UpdateProjectRequest(
    val name: String,
    val description: String?,
)
