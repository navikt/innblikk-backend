package no.nav.reops.cohort

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Flat (non-self-joining) JPA entity for one node in a cohort's criteria tree.
 * Deliberately has no `@ManyToOne` back-reference to its own parent — the tree
 * is reconstructed from a flat `List<CohortNodeEntity>` in the service layer
 * (see CohortNodeTreeBuilder), avoiding Hibernate self-join/proxy complexity.
 */
@Entity
@Table(name = "cohort_nodes")
class CohortNodeEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "cohort_id", nullable = false) var cohortId: Long,
    @Column(name = "parent_id") var parentId: Long? = null,
    @Enumerated(EnumType.STRING) @Column(name = "node_type", nullable = false) var nodeType: CohortNodeType,
    @Column(name = "ordering", nullable = false) var ordering: Int = 0,

    // GROUP
    @Enumerated(EnumType.STRING) @Column(name = "combinator") var combinator: LogicalOperator? = null,
    @Column(name = "negated", nullable = false) var negated: Boolean = false,
    @Enumerated(EnumType.STRING) @Column(name = "occurrence_operator") var occurrenceOperator: OccurrenceOperator? = null,
    @Column(name = "occurrence_count") var occurrenceCount: Int? = null,

    // CONDITION
    @Column(name = "field") var field: String? = null,
    @Enumerated(EnumType.STRING) @Column(name = "condition_type") var conditionType: ComparisonOperator? = null,
    @Column(name = "value") var value: String? = null,

    // COHORT_REF
    @Column(name = "referenced_cohort_id") var referencedCohortId: Long? = null,

    // SEQUENCE
    @Enumerated(EnumType.STRING) @Column(name = "sequence_relation") var sequenceRelation: SequenceRelation? = null,
    @Column(name = "sequence_window_value") var sequenceWindowValue: Int? = null,
    @Enumerated(EnumType.STRING) @Column(name = "sequence_window_unit") var sequenceWindowUnit: SequenceTimeUnit? = null,
    @Enumerated(EnumType.STRING) @Column(name = "sequence_role") var sequenceRole: SequenceRole? = null,

    @Column(name = "updated_at", nullable = false) var updatedAt: LocalDateTime = LocalDateTime.now(),
)

enum class CohortNodeType {
    GROUP,
    CONDITION,
    COHORT_REF,
    SEQUENCE,
}

/** Tags which of a SEQUENCE node's two direct children this is. */
enum class SequenceRole {
    ANCHOR,
    TARGET,
}
