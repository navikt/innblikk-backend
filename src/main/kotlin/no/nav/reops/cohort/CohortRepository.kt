package no.nav.reops.cohort

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.util.UUID

interface CohortRepository : CrudRepository<CohortEntity, Long> {
    fun existsByNameAndWebsiteId(name: String, websiteId: UUID): Boolean

    // Only return non-deleted cohorts
    fun findAllByWebsiteIdAndDeletedAtIsNull(websiteId: UUID): List<CohortEntity>
    fun findAllByDeletedAtIsNull(): List<CohortEntity>

    @Query("SELECT e FROM CohortEntity e WHERE e.id = :id AND e.deletedAt IS NULL")
    fun findActiveById(id: Long): CohortEntity?

    @Query("SELECT e FROM CohortEntity e WHERE e.id = :id AND e.deletedAt IS NOT NULL")
    fun findDeletedById(id: Long): CohortEntity?
}

interface CohortEntryRepository : CrudRepository<CohortEntryEntity, Long> {
    fun findAllByCohortIdOrderByOrderingAsc(cohortId: Long): List<CohortEntryEntity>
    fun findByIdAndCohortId(id: Long, cohortId: Long): CohortEntryEntity?
    fun findAllByCohortIdAndIdIn(cohortId: Long, ids: List<Long>): List<CohortEntryEntity>

    @Query("SELECT COALESCE(MAX(e.ordering), -1) FROM CohortEntryEntity e WHERE e.cohort.id = :cohortId")
    fun findMaxOrderingByCohortId(cohortId: Long): Int
}

interface CohortEntryConditionRepository : CrudRepository<CohortEntryConditionEntity, Long>
