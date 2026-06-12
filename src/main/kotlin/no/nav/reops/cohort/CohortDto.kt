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
    val entries: List<CohortEntryDto>,
)

data class CohortEntryDto(
    val id: Long,
    val cohortId: Long,
    val negated: Boolean,
    val inCohort: Boolean,
    val ordering: Int,
    val operator: LogicalOperator?,
    val referencedCohortId: Long?,
    val updatedAt: LocalDateTime,
    val conditions: List<CohortEntryConditionDto>,
)

data class CohortEntryConditionDto(
    val id: Long,
    val ordering: Int,
    val conditionType: ComparisonOperator,
    val field: String,
    val value: String,
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

data class CreateCohortEntryRequest(
    val negated: Boolean = false,
    val inCohort: Boolean = false,
    val operator: LogicalOperator? = null,
    val referencedCohortId: Long? = null,
    val conditions: List<CreateCohortEntryConditionRequest> = emptyList(),
)

data class CreateCohortEntryConditionRequest(
    val conditionType: ComparisonOperator,
    val field: String,
    val value: String,
)

data class CohortEntryOrderingEntry(
    val id: Long,
    val ordering: Int,
)
