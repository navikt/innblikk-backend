package no.nav.reops.dashboard

import org.springframework.data.repository.CrudRepository

interface DashboardRepository : CrudRepository<DashboardEntity, Long> {
    fun findAllByProjectId(projectId: Long): List<DashboardEntity>
    fun findByIdAndProjectId(id: Long, projectId: Long): DashboardEntity?
    fun existsByProjectIdAndName(projectId: Long, name: String): Boolean
    fun existsByIdAndName(id: Long, name: String): Boolean
}
