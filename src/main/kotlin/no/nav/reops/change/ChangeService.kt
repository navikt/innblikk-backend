package no.nav.reops.change

import org.springframework.stereotype.Service

@Service
class ChangeService(private val changeRepository: ChangeRepository) {

    fun createChange(entityId: Long, entityType: EntityType, changeType: ChangeType): ChangeDto {
        val entity = ChangeEntity(
            entityId = entityId,
            entityType = entityType,
            changeType = changeType
        )
        val savedEntity = changeRepository.save(entity)
        return savedEntity.toDto()
    }

    fun getChangeByEntityIdAndEntityType(entityId: Long, entityType: EntityType) =
        changeRepository.findFirstByEntityTypeAndEntityIdOrderByUpdatedAtDesc(entityType, entityId)?.toDto()

    fun getAllChangeByEntityIdAndEntityType(entityId: Long, entityType: EntityType) =
        changeRepository.findByEntityTypeAndEntityIdOrderByUpdatedAtDesc(entityType, entityId).map { it.toDto() }

    private fun ChangeEntity.toDto() = ChangeDto(
        entityId = entityId,
        entityType = entityType,
        changeType = changeType,
        updatedAt = updatedAt,
    )

}