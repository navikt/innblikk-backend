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
    val condition: EntryCondition,
    val ordering: Int,
    val operator: EntryOperator?,
    val timeStart: LocalDateTime?,
    val timeEnd: LocalDateTime?,
    val timeRangeName: String?,
    val timeRangeUnit: String?,
    val updatedAt: LocalDateTime,
    val conditions: List<CohortEntryConditionDto>,
)

data class CohortEntryConditionDto(
    val id: Long,
    val ordering: Int,
    val conditionType: ConditionType,
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
    val condition: EntryCondition,
    val operator: EntryOperator? = null,
    val timeStart: LocalDateTime? = null,
    val timeEnd: LocalDateTime? = null,
    val timeRangeName: String? = null,
    val timeRangeUnit: String? = null,
    val conditions: List<CreateCohortEntryConditionRequest> = emptyList(),
)

data class CreateCohortEntryConditionRequest(
    val conditionType: ConditionType,
    val field: String,
    val value: String,
)

data class UpdateCohortEntryRequest(
    val condition: EntryCondition,
    val operator: EntryOperator? = null,
    val timeStart: LocalDateTime? = null,
    val timeEnd: LocalDateTime? = null,
    val timeRangeName: String? = null,
    val timeRangeUnit: String? = null,
    val conditions: List<CreateCohortEntryConditionRequest> = emptyList(),
)

data class CohortEntryOrderingEntry(
    val id: Long,
    val ordering: Int,
)
