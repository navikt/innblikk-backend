package no.nav.reops.cohort

import java.time.LocalDateTime
import java.util.UUID

data class CohortDto(
    val id: Long,
    val websiteId: UUID,
    val name: String,
    val description: String?,
    val updatedAt: LocalDateTime,
    val changedByEmail: String?,
    val changedByName: String?,
    val changedByNavIdent: String?,
)

data class CohortDetailDto(
    val id: Long,
    val websiteId: UUID,
    val name: String,
    val description: String?,
    val updatedAt: LocalDateTime,
    val changedByEmail: String?,
    val changedByName: String?,
    val changedByNavIdent: String?,
    /** Root of the criteria tree, or null if no criteria have been saved yet. */
    val root: CohortNodeDto?,
)

data class CreateCohortRequest(
    val websiteId: UUID,
    val name: String,
    val description: String? = null,
)

data class UpdateCohortRequest(
    val name: String,
    val description: String? = null,
    val websiteId: UUID,
)
