package no.nav.reops.query

import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import no.nav.reops.graph.GraphRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class QueryService(
    private val queryRepository: QueryRepository,
    private val graphRepository: GraphRepository,
    private val changeService: ChangeService
) {

    @Transactional
    fun createQuery(graphId: Long, request: CreateQueryRequest): QueryDto {
        validateSelectOnly(request.sqlText)

        val graph = graphRepository.findById(graphId).orElseThrow {
            IllegalArgumentException("Graph $graphId not found")
        }

        if (queryRepository.existsByGraphIdAndName(graphId, request.name)) {
            throw IllegalArgumentException("Graph with name '${request.name}' already exists in graph $graphId")
        }

        val maxOrdering = queryRepository.findMaxOrderingByGraphId(graphId)

        val entity = QueryEntity(
            graph = graph, name = request.name, sqlText = request.sqlText, ordering = maxOrdering + 1
        )

        val saved = queryRepository.save(entity)
        changeService.createChange(requireNotNull(saved.id), EntityType.QUERY, ChangeType.CREATED)
        return saved.toDto()
    }

    @Transactional(readOnly = true)
    fun getAllQuery(graphId: Long): List<QueryDto> =
        queryRepository.findAllByGraphIdOrderByOrderingAsc(graphId).map { it.toDto() }

    @Transactional(readOnly = true)
    fun getQueryById(graphId: Long, id: Long): QueryDto? = queryRepository.findByIdAndGraphId(id, graphId)?.toDto()

    @Transactional
    fun updateQuery(graphId: Long, id: Long, request: UpdateQueryRequest): QueryDto? {
        validateSelectOnly(request.sqlText)

        val entity = queryRepository.findByIdAndGraphId(id, graphId) ?: return null

        if (entity.name != request.name && queryRepository.existsByGraphIdAndName(graphId, request.name)) {
            throw IllegalArgumentException("Graph with name '${request.name}' already exists in graph $graphId")
        }

        entity.name = request.name
        entity.sqlText = request.sqlText

        val updated = queryRepository.save(entity)
        changeService.createChange(requireNotNull(updated.id), EntityType.QUERY, ChangeType.UPDATED)
        return updated.toDto()
    }

    @Transactional
    fun updateOrdering(graphId: Long, entries: List<QueryOrderingEntry>): List<QueryDto> {
        if (!graphRepository.existsById(graphId)) {
            throw IllegalArgumentException("Graph $graphId not found")
        }

        val ids = entries.map { it.id }
        val entities = queryRepository.findAllByGraphIdAndIdIn(graphId, ids).associateBy { it.id }

        val missingIds = ids.filter { it !in entities }
        if (missingIds.isNotEmpty()) {
            throw IllegalArgumentException("Queries not found in graph $graphId: $missingIds")
        }

        entries.forEach { entry ->
            entities[entry.id]!!.ordering = entry.ordering
        }

        return queryRepository.saveAll(entities.values).map { it.toDto() }.sortedBy { it.ordering }
    }

    @Transactional
    fun deleteQuery(graphId: Long, id: Long): Boolean {
        val entity = queryRepository.findByIdAndGraphId(id, graphId) ?: return false
        queryRepository.delete(entity).also {
            changeService.createChange(id, EntityType.QUERY, ChangeType.DELETED)
        }
        return true
    }

    private fun validateSelectOnly(sqlText: String) {
        val trimmed = sqlText.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Query sqlText must not be blank")
        }

        // Disallow multiple statements; also blocks trivial injection like `select 1; drop table x`.
        if (trimmed.contains(';')) {
            throw IllegalArgumentException("Only single-statement SELECT queries are allowed")
        }

        // Allow-list: SELECT or CTEs starting with WITH.
        if (!(trimmed.startsWith("select", ignoreCase = true) || trimmed.startsWith("with", ignoreCase = true))) {
            throw IllegalArgumentException("Only SELECT queries are allowed")
        }
    }

    private fun QueryEntity.toDto(): QueryDto {
        val latestChange = changeService.getChangeByEntityIdAndEntityType(requireNotNull(id), EntityType.QUERY)
        return QueryDto(
            id = requireNotNull(id),
            graphId = requireNotNull(graph.id),
            name = name,
            sqlText = sqlText,
            ordering = ordering,
            updatedAt = updatedAt,
            changedByEmail = latestChange?.changedByEmail,
            changedByName = latestChange?.changedByName,
            changedByNavIdent = latestChange?.changedByNavIdent,
        )
    }
}