package no.nav.reops.dashboard

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import no.nav.reops.graph.GraphEntity
import no.nav.reops.project.ProjectEntity
import java.time.LocalDateTime

@Entity
@Table(
    name = "dashboards",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_dashboards_project_name", columnNames = ["project_id", "name"]),
    ],
)
class DashboardEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(
        name = "project_id", nullable = false
    ) var project: ProjectEntity,

    @Column(name = "name", nullable = false) var name: String,

    @Column(name = "description") var description: String? = null,

    @Column(name = "updated_at", nullable = false) var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    @OneToMany(
        mappedBy = "dashboard",
        fetch = FetchType.LAZY,
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    var graphs: MutableSet<GraphEntity> = mutableSetOf()
}