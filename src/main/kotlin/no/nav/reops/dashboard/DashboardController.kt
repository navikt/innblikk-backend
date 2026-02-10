package no.nav.reops.dashboard

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Dashboard")
@RequestMapping("/api/projects/{projectId}/dashboards")
class DashboardController(private val dashboardService: DashboardService) {

    @PostMapping
    fun createDashboard(
        @PathVariable projectId: Long,
        @RequestBody request: CreateDashboardRequest,
    ): ResponseEntity<DashboardDto> {
        val dashboard = dashboardService.createDashboard(projectId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(dashboard)
    }

    @GetMapping
    fun getAllDashboards(@PathVariable projectId: Long): ResponseEntity<List<DashboardDto>> {
        val dashboards = dashboardService.getAllDashboards(projectId)
        return ResponseEntity.ok(dashboards)
    }

    @GetMapping("/{id}")
    fun getDashboardById(
        @PathVariable projectId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<DashboardDto> {
        val dashboard = dashboardService.getDashboardById(projectId, id)
        return if (dashboard != null) ResponseEntity.ok(dashboard) else ResponseEntity.notFound().build()
    }

    @PutMapping("/{id}")
    fun updateDashboard(
        @PathVariable projectId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateDashboardRequest,
    ): ResponseEntity<DashboardDto> {
        val updated = dashboardService.updateDashboard(projectId, id, request)
        return if (updated != null) ResponseEntity.ok(updated) else ResponseEntity.notFound().build()
    }

    @DeleteMapping("/{id}")
    fun deleteDashboard(
        @PathVariable projectId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        val deleted = dashboardService.deleteDashboard(projectId, id)
        return if (deleted) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }
}