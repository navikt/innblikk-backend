package no.nav.reops.query

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Query")
@RequestMapping("/api/projects/{projectId}/dashboards/{dashboardId}/categories/{categoryId}/graphs/{graphId}/queries")
class QueryController(private val queryService: QueryService) {

    @PostMapping
    fun createGraph(
        @PathVariable projectId: Long,
        @PathVariable dashboardId: Long,
        @PathVariable categoryId: Long,
        @PathVariable graphId: Long,
        @RequestBody request: CreateQueryRequest,
    ): ResponseEntity<QueryDto> {
        val query = queryService.createQuery(graphId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(query)
    }

    @GetMapping
    fun getAllGraphs(
        @PathVariable projectId: Long,
        @PathVariable dashboardId: Long,
        @PathVariable categoryId: Long,
        @PathVariable graphId: Long,
    ): ResponseEntity<List<QueryDto>> {
        val query = queryService.getAllQuery(graphId)
        return ResponseEntity.ok(query)
    }

    @GetMapping("/{id}")
    fun getGraphById(
        @PathVariable projectId: Long,
        @PathVariable dashboardId: Long,
        @PathVariable categoryId: Long,
        @PathVariable graphId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<QueryDto> {
        val query = queryService.getQueryById(graphId, id)
        return if (query != null) ResponseEntity.ok(query) else ResponseEntity.notFound().build()
    }

    @PutMapping("/{id}")
    fun updateGraph(
        @PathVariable projectId: Long,
        @PathVariable dashboardId: Long,
        @PathVariable categoryId: Long,
        @PathVariable graphId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateQueryRequest,
    ): ResponseEntity<QueryDto> {
        val updated = queryService.updateQuery(graphId, id, request)
        return if (updated != null) ResponseEntity.ok(updated) else ResponseEntity.notFound().build()
    }

    @DeleteMapping("/{id}")
    fun deleteGraph(
        @PathVariable projectId: Long,
        @PathVariable dashboardId: Long,
        @PathVariable categoryId: Long,
        @PathVariable graphId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        val deleted = queryService.deleteQuery(graphId, id)
        return if (deleted) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    @PostMapping("/ordering")
    fun updateOrdering(
        @PathVariable projectId: Long,
        @PathVariable dashboardId: Long,
        @PathVariable categoryId: Long,
        @PathVariable graphId: Long,
        @RequestBody entries: List<QueryOrderingEntry>,
    ): ResponseEntity<List<QueryDto>> {
        val result = queryService.updateOrdering(graphId, entries)
        return ResponseEntity.ok(result)
    }
}