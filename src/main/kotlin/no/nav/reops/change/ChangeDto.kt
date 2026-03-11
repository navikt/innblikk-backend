package no.nav.reops.change

import java.time.LocalDateTime

data class ChangeDto(
    val entityId: Long,
    val entityType: EntityType,
    val changeType: ChangeType,
    val updatedAt: LocalDateTime,
    val changedByEmail: String? = null,
    val changedByName: String? = null,
    val changedByNavIdent: String? = null,
)