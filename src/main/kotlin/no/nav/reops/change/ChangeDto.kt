package no.nav.reops.change

import java.time.LocalDateTime

data class ChangeDto(
    val entityId: Long,
    val entityType: EntityType,
    val changeType: ChangeType,
    val updatedAt: LocalDateTime,
    val changedByEmail: String = "",
    val changedByName: String = "",
    val changedByNavIdent: String = "",
)