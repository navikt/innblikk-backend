package no.nav.reops.dashboard

import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import no.nav.reops.project.ProjectRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DashboardService(
    private val dashboardRepository: DashboardRepository,
    private val projectRepository: ProjectRepository,
    private val changeService: ChangeService
) {
    @Transactional
    fun createDashboard(projectId: Long, request: CreateDashboardRequest): DashboardDto {
        val project = projectRepository.findById(projectId).orElseThrow {
            IllegalArgumentException("Project $projectId not found")
        }

        if (dashboardRepository.existsByProjectIdAndName(projectId, request.name)) {
            throw IllegalArgumentException("Dashboard with name '${request.name}' already exists in project $projectId")
        }

        val entity = DashboardEntity(
            project = project,
            name = request.name,
            description = request.description,
        )

        return dashboardRepository.save(entity).toDto().also {
            changeService.createChange(it.id, EntityType.DASHBOARD, ChangeType.CREATED)
        }
    }

    @Transactional(readOnly = true)
    fun getAllDashboards(projectId: Long): List<DashboardDto> =
        dashboardRepository.findAllByProjectId(projectId).map { it.toDto() }

    @Transactional(readOnly = true)
    fun getDashboardById(projectId: Long, id: Long): DashboardDto? =
        dashboardRepository.findByIdAndProjectId(id, projectId)?.toDto()

    @Transactional
    fun updateDashboard(projectId: Long, id: Long, request: UpdateDashboardRequest): DashboardDto? {
        val entity = dashboardRepository.findByIdAndProjectId(id, projectId) ?: return null

        val targetProjectId = request.projectId ?: projectId

        if (targetProjectId != projectId) {
            val targetProject = projectRepository.findById(targetProjectId).orElseThrow {
                IllegalArgumentException("Project $targetProjectId not found")
            }
            if (dashboardRepository.existsByProjectIdAndName(targetProjectId, request.name)) {
                throw IllegalArgumentException("Dashboard with name '${request.name}' already exists in project $targetProjectId")
            }
            entity.project = targetProject
        } else if (entity.name != request.name && dashboardRepository.existsByProjectIdAndName(projectId, request.name)) {
            throw IllegalArgumentException("Dashboard with name '${request.name}' already exists in project $projectId")
        }

        entity.name = request.name
        entity.description = request.description

        return dashboardRepository.save(entity).toDto().also {
            changeService.createChange(it.id, EntityType.DASHBOARD, ChangeType.UPDATED)
        }
    }

    @Transactional
    fun deleteDashboard(projectId: Long, id: Long): Boolean {
        val entity = dashboardRepository.findByIdAndProjectId(id, projectId) ?: return false
        dashboardRepository.delete(entity).also {
            changeService.createChange(id, EntityType.DASHBOARD, ChangeType.DELETED)
        }
        return true
    }

    private fun DashboardEntity.toDto() = DashboardDto(
        id = requireNotNull(id),
        projectId = requireNotNull(project.id),
        name = name,
        description = description,
        updatedAt = updatedAt,
    )
}