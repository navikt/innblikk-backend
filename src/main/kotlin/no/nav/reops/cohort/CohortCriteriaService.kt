package no.nav.reops.cohort

import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Replaces a cohort's entire criteria tree in one transactional call
 * (PUT /api/cohort/{id}/criteria) — validates the incoming tree, deletes any
 * existing nodes, then persists the new tree top-down.
 */
@Service
class CohortCriteriaService(
    private val cohortRepository: CohortRepository,
    private val cohortNodeRepository: CohortNodeRepository,
    private val changeService: ChangeService,
    private val validator: CohortTreeValidator = CohortTreeValidator(),
) {

    @Transactional
    fun replaceCriteria(cohortId: Long, root: CohortNodeRequest): CohortNodeDto {
        if (!cohortRepository.existsById(cohortId)) {
            throw IllegalArgumentException("Cohort $cohortId not found")
        }

        validator.validate(root)

        cohortNodeRepository.deleteAllByCohortId(cohortId)

        val persister = CohortNodeTreePersister { entity -> cohortNodeRepository.save(entity) }
        persister.persist(root, cohortId)

        changeService.createChange(cohortId, EntityType.COHORT, ChangeType.UPDATED)

        val savedNodes = cohortNodeRepository.findAllByCohortIdOrderByOrderingAsc(cohortId)
        return CohortNodeTreeBuilder.build(savedNodes)
    }
}
