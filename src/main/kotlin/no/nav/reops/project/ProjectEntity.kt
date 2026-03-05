package no.nav.reops.project

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import no.nav.reops.dashboard.DashboardEntity
import java.time.LocalDateTime

@Entity
@Table(
    name = "projects",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_projects_name", columnNames = ["name"]),
    ],
)
class ProjectEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,

    @Column(name = "name", nullable = false) var name: String,

    @Column(name = "description") var description: String? = null,

    @Column(name = "updated_at", nullable = false) var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    @OneToMany(
        mappedBy = "project",
        fetch = FetchType.LAZY,
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    var dashboards: MutableList<DashboardEntity> = mutableListOf()
}