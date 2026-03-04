package no.nav.reops.query

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import no.nav.reops.graph.GraphEntity
import java.time.LocalDateTime

@Entity
@Table(
    name = "queries",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_queries_graph_name", columnNames = ["graph_id", "name"]),
    ],
)
class QueryEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(
        name = "graph_id", nullable = false
    ) var graph: GraphEntity,

    @Column(name = "name", nullable = false) var name: String,

    @Column(name = "sql_text", nullable = false) var sqlText: String,

    @Column(name = "ordering", nullable = false) var ordering: Int = 0,

    @Column(name = "updated_at", nullable = false) var updatedAt: LocalDateTime = LocalDateTime.now(),
)