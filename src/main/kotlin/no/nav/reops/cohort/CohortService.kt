package no.nav.reops.cohort

import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

@Service
class CohortService(
    private val cohortRepository: CohortRepository,
    private val changeService: ChangeService,
) {

    @Transactional
    fun createCohort(request: CreateCohortRequest): CohortDto {
        if (cohortRepository.existsByName(request.name)) {
            throw IllegalArgumentException("Cohort with name '${request.name}' already exists")
        }

        val entity = CohortEntity(
            websiteId = request.websiteId,
            name = request.name,
            description = request.description,
        )

        val saved = cohortRepository.save(entity)
        changeService.createChange(requireNotNull(saved.id), EntityType.COHORT, ChangeType.CREATED)
        return saved.toDto()
    }

    @Transactional(readOnly = true)
    fun getAllCohorts(): List<CohortDto> = cohortRepository.findAll().map { it.toDto() }

    @Transactional(readOnly = true)
    fun getCohortsByWebsiteId(websiteId: UUID): List<CohortDto> =
        cohortRepository.findAllByWebsiteId(websiteId).map { it.toDto() }

    @Transactional(readOnly = true)
    fun getCohortById(id: Long): CohortDetailDto? =
        cohortRepository.findById(id).getOrNull()?.toDetailDto()

    @Transactional
    fun updateCohort(id: Long, request: UpdateCohortRequest): CohortDto? {
        val entity = cohortRepository.findById(id).getOrNull() ?: return null

        if (entity.name != request.name && cohortRepository.existsByName(request.name)) {
            throw IllegalArgumentException("Cohort with name '${request.name}' already exists")
        }

        entity.name = request.name
        entity.description = request.description
        entity.websiteId = request.websiteId

        val updated = cohortRepository.save(entity)
        changeService.createChange(requireNotNull(updated.id), EntityType.COHORT, ChangeType.UPDATED)
        return updated.toDto()
    }

    @Transactional
    fun deleteCohort(id: Long): Boolean {
        val entity = cohortRepository.findById(id).getOrNull() ?: return false
        cohortRepository.delete(entity).also {
            changeService.createChange(id, EntityType.COHORT, ChangeType.DELETED)
        }
        return true
    }

    private fun CohortEntity.toDto(): CohortDto {
        val latestChange = changeService.getChangeByEntityIdAndEntityType(requireNotNull(id), EntityType.COHORT)
        return CohortDto(
            id = requireNotNull(id),
            websiteId = websiteId,
            name = name,
            description = description,
            updatedAt = updatedAt,
            changedByEmail = latestChange?.changedByEmail,
            changedByName = latestChange?.changedByName,
            changedByNavIdent = latestChange?.changedByNavIdent,
        )
    }

    private fun CohortEntity.toDetailDto(): CohortDetailDto {
        val latestChange = changeService.getChangeByEntityIdAndEntityType(requireNotNull(id), EntityType.COHORT)
        return CohortDetailDto(
            id = requireNotNull(id),
            websiteId = websiteId,
            name = name,
            description = description,
            updatedAt = updatedAt,
            changedByEmail = latestChange?.changedByEmail,
            changedByName = latestChange?.changedByName,
            changedByNavIdent = latestChange?.changedByNavIdent,
            entries = entries.sortedBy { it.ordering }.map { it.toEntryDto() },
        )
    }

    private fun CohortEntryEntity.toEntryDto(): CohortEntryDto = CohortEntryDto(
        id = requireNotNull(id),
        cohortId = requireNotNull(cohort.id),
        condition = condition,
        ordering = ordering,
        operator = operator,
        referencedCohortId = referencedCohortId,
        timeScope = timeScope,
        timeRangeType = timeRangeType,
        timeRangeValue = timeRangeValue,
        timeRangeUnit = timeRangeUnit,
        timeQualifier = timeQualifier,
        timeStart = timeStart,
        timeEnd = timeEnd,
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
