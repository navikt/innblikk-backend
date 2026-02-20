package no.nav.reops.graph

import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import no.nav.reops.dashboard.DashboardRepository
import org.springframework.stereotype.Service

@Service
class GraphService(
    private val graphRepository: GraphRepository,
    private val dashboardRepository: DashboardRepository,
    private val changeService: ChangeService
) {

    fun createGraph(dashboardId: Long, request: CreateGraphRequest): GraphDto {
        val dashboard = dashboardRepository.findById(dashboardId).orElseThrow {
            IllegalArgumentException("Dashboard $dashboardId not found")
        }

        if (graphRepository.existsByDashboardIdAndName(dashboardId, request.name)) {
            throw IllegalArgumentException("Graph with name '${request.name}' already exists in dashboard $dashboardId")
        }

        val entity = GraphEntity(
            dashboard = dashboard, name = request.name, graphType = request.graphType, width = request.width ?: 50
        )

        return graphRepository.save(entity).toDto().also {
            changeService.createChange(it.id, EntityType.GRAPH, ChangeType.CREATED)
        }
    }

    fun getAllGraphs(dashboardId: Long): List<GraphDto> =
        graphRepository.findAllByDashboardId(dashboardId).map { it.toDto() }

    fun getGraphById(dashboardId: Long, id: Long): GraphDto? =
        graphRepository.findByIdAndDashboardId(id, dashboardId)?.toDto()

    fun updateGraph(dashboardId: Long, id: Long, request: UpdateGraphRequest): GraphDto? {
        val entity = graphRepository.findByIdAndDashboardId(id, dashboardId) ?: return null

        if (entity.name != request.name && graphRepository.existsByDashboardIdAndName(dashboardId, request.name)) {
            throw IllegalArgumentException("Graph with name '${request.name}' already exists in dashboard $dashboardId")
        }

        entity.name = request.name
        entity.graphType = request.graphType
        entity.width = request.width ?: entity.width

        return graphRepository.save(entity).toDto().also {
            changeService.createChange(it.id, EntityType.GRAPH, ChangeType.UPDATED)
        }
    }

    fun deleteGraph(dashboardId: Long, id: Long): Boolean {
        val entity = graphRepository.findByIdAndDashboardId(id, dashboardId) ?: return false
        graphRepository.delete(entity).also {
            changeService.createChange(id, EntityType.GRAPH, ChangeType.DELETED)
        }
        return true
    }

    private fun GraphEntity.toDto() = GraphDto(
        id = requireNotNull(id),
        dashboardId = requireNotNull(dashboard.id),
        name = name,
        graphType = graphType,
        width = width,
        updatedAt = updatedAt,
    )
}