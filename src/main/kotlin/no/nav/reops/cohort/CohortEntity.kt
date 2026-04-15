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
import com.fasterxml.jackson.annotation.JsonValue
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "cohorts",
    uniqueConstraints = [UniqueConstraint(name = "uq_cohorts_name", columnNames = ["name"])],
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
    @Enumerated(EnumType.STRING) @Column(name = "condition", nullable = false) var condition: EntryCondition,
    @Enumerated(EnumType.STRING) @Column(name = "operator") var operator: EntryOperator? = null,
    @Column(name = "time_start") var timeStart: LocalDateTime? = null,
    @Column(name = "time_end") var timeEnd: LocalDateTime? = null,
    @Column(name = "time_range_name") var timeRangeName: String? = null,
    @Column(name = "time_range_unit") var timeRangeUnit: String? = null,
    @Column(name = "ordering", nullable = false) var ordering: Int = 0,
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
    @Enumerated(EnumType.STRING) @Column(name = "condition_type", nullable = false) var conditionType: ConditionType,
    @Column(name = "field", nullable = false) var field: String,
    @Column(name = "value", nullable = false) var value: String,
)

enum class EntryCondition {
    NOT, DID
}

enum class EntryOperator {
    AND
}

enum class ConditionType(@get:JsonValue val displayName: String) {
    NOT_LIKE("NOT LIKE"),
    WITH("WITH"),
}
