package no.nav.reops.graph

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Graph")
@RequestMapping("/api/projects/{projectId}/dashboards/{dashboardId}/graphs")
class GraphController(private val graphService: GraphService) {

    @PostMapping
    fun createGraph(
        @PathVariable projectId: Long,
        @PathVariable dashboardId: Long,
        @RequestBody request: CreateGraphRequest,
    ): ResponseEntity<GraphDto> {
        val graph = graphService.createGraph(dashboardId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(graph)
    }

    @GetMapping
    fun getAllGraphs(
        @PathVariable projectId: Long, @PathVariable dashboardId: Long
    ): ResponseEntity<List<GraphDto>> {
        val graph = graphService.getAllGraphs(dashboardId)
        return ResponseEntity.ok(graph)
    }

    @GetMapping("/{id}")
    fun getGraphById(
        @PathVariable projectId: Long,
        @PathVariable dashboardId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<GraphDto> {
        val graph = graphService.getGraphById(dashboardId, id)
        return if (graph != null) ResponseEntity.ok(graph) else ResponseEntity.notFound().build()
    }

    @PutMapping("/{id}")
    fun updateGraph(
        @PathVariable projectId: Long,
        @PathVariable dashboardId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateGraphRequest,
    ): ResponseEntity<GraphDto> {
        val updated = graphService.updateGraph(dashboardId, id, request)
        return if (updated != null) ResponseEntity.ok(updated) else ResponseEntity.notFound().build()
    }

    @DeleteMapping("/{id}")
    fun deleteGraph(
        @PathVariable projectId: Long,
        @PathVariable dashboardId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        val deleted = graphService.deleteGraph(dashboardId, id)
        return if (deleted) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    @PutMapping("/ordering")
    fun updateOrdering(
        @PathVariable projectId: Long,
        @PathVariable dashboardId: Long,
        @RequestBody entries: List<GraphOrderingEntry>,
    ): ResponseEntity<List<GraphDto>> {
        val result = graphService.updateOrdering(dashboardId, entries)
        return ResponseEntity.ok(result)
    }
}