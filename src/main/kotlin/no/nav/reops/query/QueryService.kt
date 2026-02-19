package no.nav.reops.query

import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import no.nav.reops.graph.GraphRepository
import org.springframework.stereotype.Service

@Service
class QueryService(
    private val queryRepository: QueryRepository,
    private val graphRepository: GraphRepository,
    private val changeService: ChangeService
) {

    fun createQuery(graphId: Long, request: CreateQueryRequest): QueryDto {
        validateSelectOnly(request.sqlText)

        val graph = graphRepository.findById(graphId).orElseThrow {
            IllegalArgumentException("Graph $graphId not found")
        }

        if (queryRepository.existsByGraphIdAndName(graphId, request.name)) {
            throw IllegalArgumentException("Graph with name '${request.name}' already exists in graph $graphId")
        }

        val entity = QueryEntity(
            graph = graph, name = request.name, sqlText = request.sqlText
        )

        return queryRepository.save(entity).toDto().also {
            changeService.createChange(it.id, EntityType.QUERY, ChangeType.CREATED)
        }
    }

    fun getAllQuery(graphId: Long): List<QueryDto> = queryRepository.findAllByGraphId(graphId).map { it.toDto() }

    fun getQueryById(graphId: Long, id: Long): QueryDto? = queryRepository.findByIdAndGraphId(id, graphId)?.toDto()

    fun updateQuery(graphId: Long, id: Long, request: UpdateQueryRequest): QueryDto? {
        validateSelectOnly(request.sqlText)

        val entity = queryRepository.findByIdAndGraphId(id, graphId) ?: return null

        if (entity.name != request.name && queryRepository.existsByGraphIdAndName(graphId, request.name)) {
            throw IllegalArgumentException("Graph with name '${request.name}' already exists in graph $graphId")
        }

        entity.name = request.name
        entity.sqlText = request.sqlText

        return queryRepository.save(entity).toDto().also {
            changeService.createChange(it.id, EntityType.QUERY, ChangeType.UPDATED)
        }
    }

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

    private fun QueryEntity.toDto() = QueryDto(
        id = requireNotNull(id),
        graphId = requireNotNull(graph.id),
        name = name,
        sqlText = sqlText,
        updatedAt = updatedAt,
    )
}