package no.nav.reops.sidegroup

import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull

@Service
class SidegroupService(
    private val sidegroupRepository: SidegroupRepository, private val changeService: ChangeService
) {
    @Transactional
    fun createSidegroup(request: CreateSidegroupRequest): SidegroupDto {
        if (sidegroupRepository.existsByName(request.name)) {
            throw IllegalArgumentException("Sidegroup with name '${request.name}' already exists")
        }

        val entity = SidegroupEntity(
            name = request.name,
            description = request.description,
            websiteId = request.websiteId,
            include = request.include ?: emptyList(),
            exclude = request.exclude ?: emptyList(),
            exact = request.exact ?: emptyList(),
            startWith = request.startWith ?: emptyList(),
            endWith = request.endWith ?: emptyList(),
        )

        val saved = sidegroupRepository.save(entity)
        changeService.createChange(requireNotNull(saved.id), EntityType.SIDEGROUP, ChangeType.CREATED)
        return saved.toDto()
    }

    @Transactional(readOnly = true)
    fun getAllSidegroup(): List<SidegroupDto> = sidegroupRepository.findAll().map { it.toDto() }

    @Transactional(readOnly = true)
    fun getSidegroupById(id: Long): SidegroupDto? = sidegroupRepository.findById(id).getOrNull()?.toDto()

    @Transactional
    fun updateSidegroup(id: Long, request: UpdateSidegroupRequest): SidegroupDto? {
        val entity = sidegroupRepository.findById(id).getOrNull() ?: return null
        entity.name = request.name
        entity.description = request.description
        request.websiteId?.let { entity.websiteId = it }
        request.include?.let { entity.include = it }
        request.exclude?.let { entity.exclude = it }
        request.exact?.let { entity.exact = it }
        request.startWith?.let { entity.startWith = it }
        request.endWith?.let { entity.endWith = it }

        val updated = sidegroupRepository.save(entity)
        changeService.createChange(requireNotNull(updated.id), EntityType.SIDEGROUP, ChangeType.UPDATED)
        return updated.toDto()
    }

    @Transactional
    fun deleteSidegroup(id: Long): Boolean {
        val entity = sidegroupRepository.findById(id).getOrNull() ?: return false
        sidegroupRepository.delete(entity).also {
            changeService.createChange(id, EntityType.SIDEGROUP, ChangeType.DELETED)
        }
        return true
    }

    private fun SidegroupEntity.toDto(): SidegroupDto {
        val latestChange = changeService.getChangeByEntityIdAndEntityType(requireNotNull(id), EntityType.SIDEGROUP)
        return SidegroupDto(
            id = requireNotNull(id),
            name = name,
            description = description,
            websiteId = websiteId,
            include = include,
            exclude = exclude,
            exact = exact,
            startWith = startWith,
            endWith = endWith,
            updatedAt = updatedAt,
            changedByEmail = latestChange?.changedByEmail,
            changedByName = latestChange?.changedByName,
            changedByNavIdent = latestChange?.changedByNavIdent,
        )
    }
}