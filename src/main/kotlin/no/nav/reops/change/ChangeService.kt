package no.nav.reops.change

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChangeService(private val changeRepository: ChangeRepository) {

    @Transactional
    fun createChange(entityId: Long, entityType: EntityType, changeType: ChangeType): ChangeDto {
        val entity = ChangeEntity(
            entityId = entityId,
            entityType = entityType,
            changeType = changeType
        )
        val savedEntity = changeRepository.save(entity)
        return savedEntity.toDto()
    }

    @Transactional(readOnly = true)
    fun getChangeByEntityIdAndEntityType(entityId: Long, entityType: EntityType) =
        changeRepository.findFirstByEntityTypeAndEntityIdOrderByUpdatedAtDesc(entityType, entityId)?.toDto()

    @Transactional(readOnly = true)
    fun getAllChangeByEntityIdAndEntityType(entityId: Long, entityType: EntityType) =
        changeRepository.findByEntityTypeAndEntityIdOrderByUpdatedAtDesc(entityType, entityId).map { it.toDto() }

    private fun ChangeEntity.toDto() = ChangeDto(
        entityId = entityId,
        entityType = entityType,
        changeType = changeType,
        updatedAt = updatedAt,
    )

}