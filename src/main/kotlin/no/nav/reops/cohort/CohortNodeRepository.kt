package no.nav.reops.cohort

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface CohortNodeRepository : CrudRepository<CohortNodeEntity, Long> {
    fun findAllByCohortIdOrderByOrderingAsc(cohortId: Long): List<CohortNodeEntity>

    /**
     * A single bulk DELETE statement, not Spring Data's default derived-delete
     * behavior (select-then-remove-each-entity). The latter conflicts with the
     * self-referential ON DELETE CASCADE on parent_id: deleting the root row
     * cascades away its descendants at the DB level, then Hibernate's per-entity
     * loop tries to explicitly delete those already-gone rows too, throwing a
     * StaleObjectStateException. A single bulk statement avoids the race entirely.
     */
    @Modifying
    @Query("DELETE FROM CohortNodeEntity e WHERE e.cohortId = :cohortId")
    fun deleteAllByCohortId(cohortId: Long)
}
