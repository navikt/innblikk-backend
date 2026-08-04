package no.nav.reops.cohort

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Recursive request tree for PUT /api/cohort/{id}/criteria.
 *
 * Replaces the old flat CohortEntry/CohortEntryCondition CRUD shape with a
 * self-referential tree that supports arbitrary AND/OR/NOT nesting, cohort
 * references, and event-sequence ("X then NOT Y within N days") conditions.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "nodeType")
@JsonSubTypes(
    JsonSubTypes.Type(value = CohortNodeRequest.GroupNode::class, name = "GROUP"),
    JsonSubTypes.Type(value = CohortNodeRequest.ConditionNode::class, name = "CONDITION"),
    JsonSubTypes.Type(value = CohortNodeRequest.CohortRefNode::class, name = "COHORT_REF"),
    JsonSubTypes.Type(value = CohortNodeRequest.SequenceNode::class, name = "SEQUENCE"),
)
sealed interface CohortNodeRequest {

    /** A boolean group combining its children with AND/OR, optionally negated. */
    data class GroupNode(
        val combinator: LogicalOperator = LogicalOperator.AND,
        val negated: Boolean = false,
        // Reserved for a future pass — count-threshold semantics (e.g. "count >= 3").
        // Not implemented yet: currently every group is implicitly count >= 1 (EXISTS).
        val occurrenceOperator: OccurrenceOperator? = null,
        val occurrenceCount: Int? = null,
        val children: List<CohortNodeRequest> = emptyList(),
    ) : CohortNodeRequest

    /**
     * A single field predicate — a leaf.
     *
     * Exactly one of [field]/[paramKey] must be set: [field] for a real
     * event/session column, [paramKey] for a custom event parameter stored in
     * `event_data.event_parameters` (key/value pairs, e.g. a form field's
     * `tekst`/`valg`/`data`). Two conditions on different param keys within the
     * same group correlate to the same underlying event row via separate
     * `UNNEST` joins in the resolver, not a plain column match.
     */
    data class ConditionNode(
        val field: String? = null,
        val paramKey: String? = null,
        val conditionType: ComparisonOperator,
        val value: String,
    ) : CohortNodeRequest

    /** References another cohort by id — "is/is-not in cohort X". A leaf. */
    data class CohortRefNode(
        val referencedCohortId: Long,
        val negated: Boolean = false,
    ) : CohortNodeRequest

    /**
     * "Did [anchor], then [relation] [target] within [windowValue] [windowUnit] of anchor."
     *
     * Anchor/target subtrees must resolve to actual timestamped event rows —
     * they may only contain GroupNode/ConditionNode (no CohortRefNode, no nested
     * SequenceNode), since there is no single "occurrence timestamp" for those.
     */
    data class SequenceNode(
        val anchor: GroupNode,
        val target: GroupNode,
        val relation: SequenceRelation,
        val windowValue: Int,
        val windowUnit: SequenceTimeUnit,
    ) : CohortNodeRequest
}

enum class OccurrenceOperator {
    GREATER_THAN_OR_EQUAL,
    LESS_THAN_OR_EQUAL,
    EQUAL,
}

enum class SequenceRelation {
    FOLLOWED_BY,
    NOT_FOLLOWED_BY,
}

enum class SequenceTimeUnit {
    MINUTE,
    HOUR,
    DAY,
    WEEK,
    MONTH,
    YEAR,
}
