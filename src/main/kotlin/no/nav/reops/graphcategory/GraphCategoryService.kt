package no.nav.reops.graphcategory

import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import no.nav.reops.dashboard.DashboardRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GraphCategoryService(
    private val graphCategoryRepository: GraphCategoryRepository,
    private val dashboardRepository: DashboardRepository,
    private val changeService: ChangeService,
) {

    @Transactional
    fun createCategory(dashboardId: Long, request: CreateGraphCategoryRequest): GraphCategoryDto {
        val dashboard = dashboardRepository.findById(dashboardId).orElseThrow {
            IllegalArgumentException("Dashboard $dashboardId not found")
        }

        if (graphCategoryRepository.existsByDashboardIdAndName(dashboardId, request.name)) {
            throw IllegalArgumentException("Category with name '${request.name}' already exists in dashboard $dashboardId")
        }

        val maxOrdering = graphCategoryRepository.findMaxOrderingByDashboardId(dashboardId)

        val entity = GraphCategoryEntity(
            dashboard = dashboard,
            name = request.name,
            ordering = maxOrdering + 1,
        )

        return graphCategoryRepository.save(entity).toDto().also {
            changeService.createChange(it.id, EntityType.GRAPH_CATEGORY, ChangeType.CREATED)
        }
    }

    @Transactional(readOnly = true)
    fun getAllCategories(dashboardId: Long): List<GraphCategoryDto> =
        graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(dashboardId).map { it.toDto() }

    @Transactional(readOnly = true)
    fun getCategoryById(dashboardId: Long, id: Long): GraphCategoryDto? =
        graphCategoryRepository.findByIdAndDashboardId(id, dashboardId)?.toDto()

    @Transactional
    fun updateCategory(dashboardId: Long, id: Long, request: UpdateGraphCategoryRequest): GraphCategoryDto? {
        val entity = graphCategoryRepository.findByIdAndDashboardId(id, dashboardId) ?: return null

        if (entity.name != request.name && graphCategoryRepository.existsByDashboardIdAndName(dashboardId, request.name)) {
            throw IllegalArgumentException("Category with name '${request.name}' already exists in dashboard $dashboardId")
        }

        entity.name = request.name

        return graphCategoryRepository.save(entity).toDto().also {
            changeService.createChange(it.id, EntityType.GRAPH_CATEGORY, ChangeType.UPDATED)
        }
    }

    @Transactional
    fun updateOrdering(dashboardId: Long, entries: List<GraphCategoryOrderingEntry>): List<GraphCategoryDto> {
        if (!dashboardRepository.existsById(dashboardId)) {
            throw IllegalArgumentException("Dashboard $dashboardId not found")
        }

        val ids = entries.map { it.id }
        val entities = graphCategoryRepository.findAllByDashboardIdAndIdIn(dashboardId, ids).associateBy { it.id }

        val missingIds = ids.filter { it !in entities }
        if (missingIds.isNotEmpty()) {
            throw IllegalArgumentException("Categories not found in dashboard $dashboardId: $missingIds")
        }

        entries.forEach { entry ->
            entities[entry.id]!!.ordering = entry.ordering
        }

        return graphCategoryRepository.saveAll(entities.values).map { it.toDto() }.sortedBy { it.ordering }
    }

    @Transactional
    fun deleteCategory(dashboardId: Long, id: Long): Boolean {
        val entity = graphCategoryRepository.findByIdAndDashboardId(id, dashboardId) ?: return false
        graphCategoryRepository.delete(entity).also {
            changeService.createChange(id, EntityType.GRAPH_CATEGORY, ChangeType.DELETED)
        }
        return true
    }

    private fun GraphCategoryEntity.toDto() = GraphCategoryDto(
        id = requireNotNull(id),
        dashboardId = requireNotNull(dashboard.id),
        name = name,
        ordering = ordering,
        updatedAt = updatedAt,
    )
}

