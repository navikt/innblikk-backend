package no.nav.reops.graph

import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import no.nav.reops.graphcategory.GraphCategoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GraphService(
    private val graphRepository: GraphRepository,
    private val graphCategoryRepository: GraphCategoryRepository,
    private val changeService: ChangeService
) {

    @Transactional
    fun createGraph(categoryId: Long, request: CreateGraphRequest): GraphDto {
        val category = graphCategoryRepository.findById(categoryId).orElseThrow {
            IllegalArgumentException("Category $categoryId not found")
        }

        if (graphRepository.existsByCategoryIdAndName(categoryId, request.name)) {
            throw IllegalArgumentException("Graph with name '${request.name}' already exists in category $categoryId")
        }

        val maxOrdering = graphRepository.findMaxOrderingByCategoryId(categoryId)

        val entity = GraphEntity(
            category = category,
            name = request.name,
            graphType = request.graphType,
            width = request.width ?: 50,
            ordering = maxOrdering + 1,
        )

        return graphRepository.save(entity).toDto().also {
            changeService.createChange(it.id, EntityType.GRAPH, ChangeType.CREATED)
        }
    }

    @Transactional(readOnly = true)
    fun getAllGraphs(categoryId: Long): List<GraphDto> =
        graphRepository.findAllByCategoryIdOrderByOrderingAsc(categoryId).map { it.toDto() }

    @Transactional(readOnly = true)
    fun getGraphById(categoryId: Long, id: Long): GraphDto? =
        graphRepository.findByIdAndCategoryId(id, categoryId)?.toDto()

    @Transactional
    fun updateGraph(categoryId: Long, id: Long, request: UpdateGraphRequest): GraphDto? {
        val entity = graphRepository.findByIdAndCategoryId(id, categoryId) ?: return null

        if (entity.name != request.name && graphRepository.existsByCategoryIdAndName(categoryId, request.name)) {
            throw IllegalArgumentException("Graph with name '${request.name}' already exists in category $categoryId")
        }

        entity.name = request.name
        entity.graphType = request.graphType
        entity.width = request.width ?: entity.width

        return graphRepository.save(entity).toDto().also {
            changeService.createChange(it.id, EntityType.GRAPH, ChangeType.UPDATED)
        }
    }

    @Transactional
    fun updateOrdering(categoryId: Long, entries: List<GraphOrderingEntry>): List<GraphDto> {
        if (!graphCategoryRepository.existsById(categoryId)) {
            throw IllegalArgumentException("Category $categoryId not found")
        }

        val ids = entries.map { it.id }
        val entities = graphRepository.findAllByCategoryIdAndIdIn(categoryId, ids).associateBy { it.id }

        val missingIds = ids.filter { it !in entities }
        if (missingIds.isNotEmpty()) {
            throw IllegalArgumentException("Graphs not found in category $categoryId: $missingIds")
        }

        entries.forEach { entry ->
            entities[entry.id]!!.ordering = entry.ordering
        }

        return graphRepository.saveAll(entities.values).map { it.toDto() }.sortedBy { it.ordering }
    }

    @Transactional
    fun deleteGraph(categoryId: Long, id: Long): Boolean {
        val entity = graphRepository.findByIdAndCategoryId(id, categoryId) ?: return false
        graphRepository.delete(entity).also {
            changeService.createChange(id, EntityType.GRAPH, ChangeType.DELETED)
        }
        return true
    }

    private fun GraphEntity.toDto() = GraphDto(
        id = requireNotNull(id),
        categoryId = requireNotNull(category.id),
        name = name,
        graphType = graphType,
        width = width,
        ordering = ordering,
        updatedAt = updatedAt,
    )
}