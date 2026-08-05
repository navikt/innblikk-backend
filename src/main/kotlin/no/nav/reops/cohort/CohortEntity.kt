package no.nav.reops.cohort

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "cohorts")
class CohortEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "website_id", nullable = false) var websiteId: UUID,
    @Column(name = "name", nullable = false) var name: String,
    @Column(name = "description") var description: String? = null,
    @Column(name = "deleted_at") var deletedAt: LocalDateTime? = null,
    @Column(name = "updated_at", nullable = false) var updatedAt: LocalDateTime = LocalDateTime.now(),
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
