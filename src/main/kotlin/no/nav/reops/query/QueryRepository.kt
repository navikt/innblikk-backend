package no.nav.reops.query

import org.springframework.data.repository.CrudRepository

interface QueryRepository : CrudRepository<QueryEntity, Long> {
    fun findAllByGraphIdOrderByOrderingAsc(graphId: Long): List<QueryEntity>
    fun findAllByGraphId(dashboardId: Long): List<QueryEntity>
    fun findByIdAndGraphId(id: Long, dashboardId: Long): QueryEntity?
    fun existsByGraphIdAndName(dashboardId: Long, name: String): Boolean
    fun existsByIdAndName(id: Long, name: String): Boolean
    fun findAllByGraphIdAndIdIn(graphId: Long, ids: List<Long>): List<QueryEntity>
}