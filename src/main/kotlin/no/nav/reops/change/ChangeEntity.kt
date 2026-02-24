package no.nav.reops.change

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "changes")
class ChangeEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,

    @Column(name = "entity_type", nullable = false) @Enumerated(EnumType.STRING) var entityType: EntityType,

    @Column(name = "entity_id", nullable = false) var entityId: Long,

    @Column(name = "change_type", nullable = false) @Enumerated(EnumType.STRING) var changeType: ChangeType,

    @Column(name = "updated_at", nullable = false) var updatedAt: LocalDateTime = LocalDateTime.now(),
)

enum class ChangeType {
    CREATED, UPDATED, DELETED
}

enum class EntityType {
    PROJECT, DASHBOARD, GRAPH_CATEGORY, GRAPH, QUERY
}