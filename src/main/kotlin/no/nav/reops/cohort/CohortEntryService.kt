package no.nav.reops.cohort

import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CohortEntryService(
    private val cohortRepository: CohortRepository,
    private val cohortEntryRepository: CohortEntryRepository,
    private val changeService: ChangeService,
) {

    @Transactional
    fun createEntry(cohortId: Long, request: CreateCohortEntryRequest): CohortEntryDto {
        val cohort = cohortRepository.findById(cohortId).orElseThrow {
            IllegalArgumentException("Cohort $cohortId not found")
        }

        val maxOrdering = cohortEntryRepository.findMaxOrderingByCohortId(cohortId)

        val entry = CohortEntryEntity(
            cohort = cohort,
            negated = request.negated,
            inCohort = request.inCohort,
            operator = request.operator,
            referencedCohortId = request.referencedCohortId,
            ordering = maxOrdering + 1,
        )

        request.conditions.forEachIndexed { index, condReq ->
            entry.conditions.add(
                CohortEntryConditionEntity(
                    cohortEntry = entry,
                    ordering = index + 1,
                    conditionType = condReq.conditionType,
                    field = condReq.field,
                    value = condReq.value,
                )
            )
        }

        val saved = cohortEntryRepository.save(entry)
        changeService.createChange(requireNotNull(saved.id), EntityType.COHORT_ENTRY, ChangeType.CREATED)
        return saved.toDto()
    }

    @Transactional
    fun updateEntry(cohortId: Long, id: Long, request: CreateCohortEntryRequest): CohortEntryDto? {
        val entry = cohortEntryRepository.findByIdAndCohortId(id, cohortId) ?: return null

        entry.negated = request.negated
        entry.inCohort = request.inCohort
        entry.operator = request.operator
        entry.referencedCohortId = request.referencedCohortId

        entry.conditions.clear()
        request.conditions.forEachIndexed { index, condReq ->
            entry.conditions.add(
                CohortEntryConditionEntity(
                    cohortEntry = entry,
                    ordering = index + 1,
                    conditionType = condReq.conditionType,
                    field = condReq.field,
                    value = condReq.value,
                )
            )
        }

        val updated = cohortEntryRepository.save(entry)
        changeService.createChange(requireNotNull(updated.id), EntityType.COHORT_ENTRY, ChangeType.UPDATED)
        return updated.toDto()
    }

    @Transactional
    fun updateOrdering(cohortId: Long, orderingEntries: List<CohortEntryOrderingEntry>): List<CohortEntryDto> {
        if (!cohortRepository.existsById(cohortId)) {
            throw IllegalArgumentException("Cohort $cohortId not found")
        }

        val ids = orderingEntries.map { it.id }
        val entities = cohortEntryRepository.findAllByCohortIdAndIdIn(cohortId, ids).associateBy { it.id }

        val missingIds = ids.filter { it !in entities }
        if (missingIds.isNotEmpty()) {
            throw IllegalArgumentException("Entries not found in cohort $cohortId: $missingIds")
        }

        orderingEntries.forEach { entry ->
            entities[entry.id]!!.ordering = entry.ordering
        }

        return cohortEntryRepository.saveAll(entities.values).map { it.toDto() }.sortedBy { it.ordering }
    }

    @Transactional
    fun deleteEntry(cohortId: Long, id: Long): Boolean {
        val entry = cohortEntryRepository.findByIdAndCohortId(id, cohortId) ?: return false
        cohortEntryRepository.delete(entry).also {
            changeService.createChange(id, EntityType.COHORT_ENTRY, ChangeType.DELETED)
        }
        return true
    }

    private fun CohortEntryEntity.toDto(): CohortEntryDto = CohortEntryDto(
        id = requireNotNull(id),
        cohortId = requireNotNull(cohort.id),
        negated = negated,
        inCohort = inCohort,
        ordering = ordering,
        operator = operator,
        referencedCohortId = referencedCohortId,
        updatedAt = updatedAt,
        conditions = conditions.sortedBy { it.ordering }.map { it.toConditionDto() },
    )

    private fun CohortEntryConditionEntity.toConditionDto(): CohortEntryConditionDto = CohortEntryConditionDto(
        id = requireNotNull(id),
        ordering = ordering,
        conditionType = conditionType,
        field = field,
        value = value,
    )
}
