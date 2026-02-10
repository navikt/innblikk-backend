package no.nav.reops.change

import org.springframework.data.repository.CrudRepository

interface ChangeRepository : CrudRepository<ChangeEntity, Long> {
    fun findFirstByEntityTypeAndEntityIdOrderByUpdatedAtDesc(entityType: EntityType, entityId: Long): ChangeEntity?
    fun findByEntityTypeAndEntityIdOrderByUpdatedAtDesc(entityType: EntityType, entityId: Long): List<ChangeEntity>
}