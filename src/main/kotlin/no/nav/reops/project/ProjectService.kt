package no.nav.reops.project

import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.repository.findByIdOrNull

@Service
class ProjectService(private val projectRepository: ProjectRepository, private val changeService: ChangeService) {

    @Transactional
    fun createProject(request: CreateProjectRequest): ProjectDto {
        val project = ProjectEntity(
            name = request.name, description = request.description
        )
        val saved = projectRepository.save(project)
        changeService.createChange(requireNotNull(saved.id), EntityType.PROJECT, ChangeType.CREATED)
        return saved.toDto()
    }

    @Transactional(readOnly = true)
    fun getAllProjects(): List<ProjectDto> {
        return projectRepository.findAll().map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun getProjectById(id: Long): ProjectDto? {
        return projectRepository.findByIdOrNull(id)?.toDto()
    }

    @Transactional
    fun updateProject(id: Long, request: UpdateProjectRequest): ProjectDto? {
        val project = projectRepository.findByIdOrNull(id) ?: return null
        project.name = request.name
        project.description = request.description
        val updated = projectRepository.save(project)
        changeService.createChange(requireNotNull(updated.id), EntityType.PROJECT, ChangeType.UPDATED)
        return updated.toDto()
    }

    @Transactional
    fun deleteProject(id: Long): Boolean {
        if (!projectRepository.existsById(id)) {
            return false
        }
        projectRepository.deleteById(id).also {
            changeService.createChange(id, EntityType.PROJECT, ChangeType.DELETED)
        }
        return true
    }

    private fun ProjectEntity.toDto(): ProjectDto {
        val latestChange = changeService.getChangeByEntityIdAndEntityType(requireNotNull(id), EntityType.PROJECT)
        return ProjectDto(
            id = requireNotNull(id),
            name = name,
            description = description,
            updatedAt = updatedAt,
            changedByEmail = latestChange?.changedByEmail,
            changedByName = latestChange?.changedByName,
            changedByNavIdent = latestChange?.changedByNavIdent,
        )
    }
}