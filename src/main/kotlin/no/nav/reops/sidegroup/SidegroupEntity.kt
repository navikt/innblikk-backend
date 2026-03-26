package no.nav.reops.sidegroup

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "sidegroups")
class SidegroupEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "name", nullable = false) var name: String,
    @Column(name = "description", nullable = true) var description: String? = null,
    @Column(name = "website_id", nullable = false) var websiteId: UUID,
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(name = "include", columnDefinition = "text[]") var include: List<String> = emptyList(),
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(name = "exclude", columnDefinition = "text[]") var exclude: List<String> = emptyList(),
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(name = "exact", columnDefinition = "text[]") var exact: List<String> = emptyList(),
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(name = "start_with", columnDefinition = "text[]") var startWith: List<String> = emptyList(),
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(name = "end_with", columnDefinition = "text[]") var endWith: List<String> = emptyList(),
    @Column(name = "updated_at", nullable = false) var updatedAt: LocalDateTime = LocalDateTime.now(),
)