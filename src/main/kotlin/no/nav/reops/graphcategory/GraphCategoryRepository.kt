package no.nav.reops.graphcategory

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface GraphCategoryRepository : CrudRepository<GraphCategoryEntity, Long> {
    fun findAllByDashboardIdOrderByOrderingAsc(dashboardId: Long): List<GraphCategoryEntity>
    fun findByIdAndDashboardId(id: Long, dashboardId: Long): GraphCategoryEntity?
    fun existsByDashboardIdAndName(dashboardId: Long, name: String): Boolean
    fun findAllByDashboardIdAndIdIn(dashboardId: Long, ids: List<Long>): List<GraphCategoryEntity>

    @Query("SELECT COALESCE(MAX(c.ordering), -1) FROM GraphCategoryEntity c WHERE c.dashboard.id = :dashboardId")
    fun findMaxOrderingByDashboardId(dashboardId: Long): Int
}

