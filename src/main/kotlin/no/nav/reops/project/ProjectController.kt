package no.nav.reops.project

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Project")
@RequestMapping("/api/projects")
class ProjectController(private val projectService: ProjectService) {

    @PostMapping
    fun createProject(@RequestBody request: CreateProjectRequest): ResponseEntity<ProjectDto> {
        val project = projectService.createProject(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(project)
    }

    @GetMapping
    fun getAllProjects(): ResponseEntity<List<ProjectDto>> {
        val projects = projectService.getAllProjects()
        return ResponseEntity.ok(projects)
    }

    @GetMapping("/{id}")
    fun getProjectById(@PathVariable id: Long): ResponseEntity<ProjectDto> {
        val project = projectService.getProjectById(id)
        return if (project != null) {
            ResponseEntity.ok(project)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PutMapping("/{id}")
    fun updateProject(
        @PathVariable id: Long,
        @RequestBody request: UpdateProjectRequest
    ): ResponseEntity<ProjectDto> {
        val updated = projectService.updateProject(id, request)
        return if (updated != null) {
            ResponseEntity.ok(updated)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/{id}")
    fun deleteProject(@PathVariable id: Long): ResponseEntity<Void> {
        val deleted = projectService.deleteProject(id)
        return if (deleted) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }
}