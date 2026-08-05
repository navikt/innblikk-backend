package no.nav.reops.cohort

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.util.UUID

interface CohortRepository : CrudRepository<CohortEntity, Long> {
    // Only return non-deleted cohorts
    fun findAllByWebsiteIdAndDeletedAtIsNull(websiteId: UUID): List<CohortEntity>
    fun findAllByDeletedAtIsNull(): List<CohortEntity>

    // Trash: only soft-deleted cohorts
    fun findAllByWebsiteIdAndDeletedAtIsNotNull(websiteId: UUID): List<CohortEntity>
    fun findAllByDeletedAtIsNotNull(): List<CohortEntity>

    @Query("SELECT e FROM CohortEntity e WHERE e.id = :id AND e.deletedAt IS NULL")
    fun findActiveById(id: Long): CohortEntity?

    @Query("SELECT e FROM CohortEntity e WHERE e.id = :id AND e.deletedAt IS NOT NULL")
    fun findDeletedById(id: Long): CohortEntity?
}
