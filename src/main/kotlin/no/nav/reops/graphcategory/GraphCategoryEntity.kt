package no.nav.reops.graphcategory

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
import no.nav.reops.dashboard.DashboardEntity
import no.nav.reops.graph.GraphEntity
import java.time.LocalDateTime

@Entity
@Table(
    name = "graph_categories",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_graph_categories_dashboard_name", columnNames = ["dashboard_id", "name"]),
    ],
)
class GraphCategoryEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(
        name = "dashboard_id", nullable = false
    ) var dashboard: DashboardEntity,

    @Column(name = "name", nullable = false) var name: String,

    @Column(name = "ordering", nullable = false) var ordering: Int = 0,

    @Column(name = "updated_at", nullable = false) var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    @OneToMany(
        mappedBy = "category",
        fetch = FetchType.LAZY,
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    var graphs: MutableSet<GraphEntity> = mutableSetOf()
}

