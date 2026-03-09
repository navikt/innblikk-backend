package no.nav.reops.graph

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import no.nav.reops.graphcategory.GraphCategoryEntity
import no.nav.reops.query.QueryEntity
import java.time.LocalDateTime

@Entity
@Table(
    name = "graphs",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_graphs_category_name", columnNames = ["category_id", "name"]),
    ],
)
class GraphEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(
        name = "category_id", nullable = false
    ) var category: GraphCategoryEntity,

    @Column(name = "name", nullable = false) var name: String,

    @Column(name = "description") var description: String? = null,

    @Enumerated(EnumType.STRING) @Column(name = "graph_type") var graphType: GraphType? = null,

    @Column(name = "width", nullable = false) var width: Int = 50,

    @Column(name = "ordering", nullable = false) var ordering: Int = 0,

    @Column(name = "updated_at", nullable = false) var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    @OneToMany(
        mappedBy = "graph",
        fetch = FetchType.LAZY,
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    var queries: MutableList<QueryEntity> = mutableListOf()
}