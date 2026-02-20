package no.nav.reops.graph

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
import no.nav.reops.query.QueryEntity
import java.time.LocalDateTime

@Entity
@Table(
    name = "graphs",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_graphs_dashboard_name", columnNames = ["dashboard_id", "name"]),
    ],
)
class GraphEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(
        name = "dashboard_id", nullable = false
    ) var dashboard: DashboardEntity,

    @Column(name = "name", nullable = false) var name: String,

    @Column(name = "graph_type") var graphType: String? = null,

    @Column(name = "width", nullable = false) var width: Int = 50,

    @Column(name = "updated_at", nullable = false) var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    @OneToMany(
        mappedBy = "graph",
        fetch = FetchType.LAZY,
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    var queries: MutableSet<QueryEntity> = mutableSetOf()
}