package no.nav.reops.cohort

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/** Recursive response tree returned by GET /api/cohort/{id}, mirrors CohortNodeRequest but with entity ids. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "nodeType")
@JsonSubTypes(
    JsonSubTypes.Type(value = CohortNodeDto.GroupNodeDto::class, name = "GROUP"),
    JsonSubTypes.Type(value = CohortNodeDto.ConditionNodeDto::class, name = "CONDITION"),
    JsonSubTypes.Type(value = CohortNodeDto.CohortRefNodeDto::class, name = "COHORT_REF"),
    JsonSubTypes.Type(value = CohortNodeDto.SequenceNodeDto::class, name = "SEQUENCE"),
)
sealed interface CohortNodeDto {
    val id: Long

    data class GroupNodeDto(
        override val id: Long,
        val combinator: LogicalOperator,
        val negated: Boolean,
        val occurrenceOperator: OccurrenceOperator? = null,
        val occurrenceCount: Int? = null,
        val children: List<CohortNodeDto> = emptyList(),
    ) : CohortNodeDto

    data class ConditionNodeDto(
        override val id: Long,
        val field: String,
        val conditionType: ComparisonOperator,
        val value: String,
    ) : CohortNodeDto

    data class CohortRefNodeDto(
        override val id: Long,
        val referencedCohortId: Long,
        val negated: Boolean,
    ) : CohortNodeDto

    data class SequenceNodeDto(
        override val id: Long,
        val anchor: GroupNodeDto,
        val target: GroupNodeDto,
        val relation: SequenceRelation,
        val windowValue: Int,
        val windowUnit: SequenceTimeUnit,
    ) : CohortNodeDto
}
