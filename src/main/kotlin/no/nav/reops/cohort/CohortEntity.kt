package no.nav.reops.cohort

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "cohorts",
    uniqueConstraints = [UniqueConstraint(name = "uq_cohorts_name_website", columnNames = ["name", "website_id"])],
)
class CohortEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "website_id", nullable = false) var websiteId: UUID,
    @Column(name = "name", nullable = false) var name: String,
    @Column(name = "description") var description: String? = null,
    @Column(name = "updated_at", nullable = false) var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    @OneToMany(mappedBy = "cohort", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var entries: MutableList<CohortEntryEntity> = mutableListOf()
}

@Entity
@Table(name = "cohort_entries")
class CohortEntryEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cohort_id", nullable = false) var cohort: CohortEntity,
    @Column(name = "negated", nullable = false) var negated: Boolean = false,
    @Column(name = "in_cohort", nullable = false) var inCohort: Boolean = false,
    @Enumerated(EnumType.STRING) @Column(name = "operator") var operator: LogicalOperator? = null,
    @Column(name = "referenced_cohort_id") var referencedCohortId: Long? = null,
    @Column(name = "ordering", nullable = false) var ordering: Int = 0,
    // Time filtering — stored but not yet used in SQL generation (TODO: implement in cohortSqlResolver)
    @Column(name = "time_value") var timeValue: Int? = null,
    @Column(name = "time_unit") var timeUnit: String? = null,
    @Column(name = "time_start") var timeStart: LocalDateTime? = null,
    @Column(name = "time_end") var timeEnd: LocalDateTime? = null,
    @Column(name = "updated_at", nullable = false) var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    @OneToMany(mappedBy = "cohortEntry", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var conditions: MutableList<CohortEntryConditionEntity> = mutableListOf()
}

@Entity
@Table(name = "cohort_entry_conditions")
class CohortEntryConditionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cohort_entry_id", nullable = false) var cohortEntry: CohortEntryEntity,
    @Column(name = "ordering", nullable = false) var ordering: Int = 0,
    @Enumerated(EnumType.STRING) @Column(name = "condition_type", nullable = false) var conditionType: ComparisonOperator,
    @Column(name = "field", nullable = false) var field: String,
    @Column(name = "value", nullable = false) var value: String,
)

enum class LogicalOperator {
    AND,
    OR,
}

enum class ComparisonOperator {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    NOT_CONTAINS,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    IN_SET,
    NOT_IN_SET,
}
