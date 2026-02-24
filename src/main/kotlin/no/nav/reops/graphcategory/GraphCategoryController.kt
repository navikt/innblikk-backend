package no.nav.reops.graphcategory

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "GraphCategory")
@RequestMapping("/api/projects/{projectId}/dashboards/{dashboardId}/categories")
class GraphCategoryController(private val graphCategoryService: GraphCategoryService) {

    @PostMapping
    fun createCategory(
        @PathVariable projectId: Long,
        @PathVariable dashboardId: Long,
        @RequestBody request: CreateGraphCategoryRequest,
    ): ResponseEntity<GraphCategoryDto> {
        val category = graphCategoryService.createCategory(dashboardId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(category)
    }

    @GetMapping
    fun getAllCategories(
        @PathVariable projectId: Long,
        @PathVariable dashboardId: Long,
    ): ResponseEntity<List<GraphCategoryDto>> {
        val categories = graphCategoryService.getAllCategories(dashboardId)
        return ResponseEntity.ok(categories)
    }

    @GetMapping("/{id}")
    fun getCategoryById(
        @PathVariable projectId: Long,
        @PathVariable dashboardId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<GraphCategoryDto> {
        val category = graphCategoryService.getCategoryById(dashboardId, id)
        return if (category != null) ResponseEntity.ok(category) else ResponseEntity.notFound().build()
    }

    @PutMapping("/{id}")
    fun updateCategory(
        @PathVariable projectId: Long,
        @PathVariable dashboardId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateGraphCategoryRequest,
    ): ResponseEntity<GraphCategoryDto> {
        val updated = graphCategoryService.updateCategory(dashboardId, id, request)
        return if (updated != null) ResponseEntity.ok(updated) else ResponseEntity.notFound().build()
    }

    @DeleteMapping("/{id}")
    fun deleteCategory(
        @PathVariable projectId: Long,
        @PathVariable dashboardId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        val deleted = graphCategoryService.deleteCategory(dashboardId, id)
        return if (deleted) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    @PostMapping("/ordering")
    fun updateOrdering(
        @PathVariable projectId: Long,
        @PathVariable dashboardId: Long,
        @RequestBody entries: List<GraphCategoryOrderingEntry>,
    ): ResponseEntity<List<GraphCategoryDto>> {
        val result = graphCategoryService.updateOrdering(dashboardId, entries)
        return ResponseEntity.ok(result)
    }
}

