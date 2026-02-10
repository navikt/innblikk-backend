package no.nav.reops.graph

import org.springframework.data.repository.CrudRepository

interface GraphRepository : CrudRepository<GraphEntity, Long> {
    fun findAllByDashboardId(dashboardId: Long): List<GraphEntity>
    fun findByIdAndDashboardId(id: Long, dashboardId: Long): GraphEntity?
    fun existsByDashboardIdAndName(dashboardId: Long, name: String): Boolean
    fun existsByIdAndName(id: Long, name: String): Boolean
}