package no.nav.reops.cohort

import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class CohortService(
    private val cohortRepository: CohortRepository,
    private val changeService: ChangeService,
) {

    @Transactional
    fun createCohort(request: CreateCohortRequest): CohortDto {
        if (cohortRepository.existsByNameAndWebsiteId(request.name, request.websiteId)) {
            throw IllegalArgumentException("Cohort with name '${request.name}' already exists for this website")
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
    fun getAllCohorts(): List<CohortDto> = cohortRepository.findAllByDeletedAtIsNull().map { it.toDto() }

    @Transactional(readOnly = true)
    fun getCohortsByWebsiteId(websiteId: UUID): List<CohortDto> =
        cohortRepository.findAllByWebsiteIdAndDeletedAtIsNull(websiteId).map { it.toDto() }

    @Transactional(readOnly = true)
    fun getCohortById(id: Long): CohortDetailDto? =
        cohortRepository.findActiveById(id)?.toDetailDto()

    @Transactional
    fun updateCohort(id: Long, request: UpdateCohortRequest): CohortDto? {
        val entity = cohortRepository.findActiveById(id) ?: return null

        if (entity.name != request.name && cohortRepository.existsByNameAndWebsiteId(request.name, request.websiteId)) {
            throw IllegalArgumentException("Cohort with name '${request.name}' already exists for this website")
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
        val entity = cohortRepository.findActiveById(id) ?: return false
        entity.deletedAt = LocalDateTime.now()
        cohortRepository.save(entity)
        changeService.createChange(id, EntityType.COHORT, ChangeType.DELETED)
        return true
    }

    @Transactional
    fun restoreCohort(id: Long): CohortDto? {
        val entity = cohortRepository.findDeletedById(id) ?: return null
        entity.deletedAt = null
        val restored = cohortRepository.save(entity)
        changeService.createChange(id, EntityType.COHORT, ChangeType.UPDATED)
        return restored.toDto()
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
