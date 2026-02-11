package no.nav.reops.dashboard

import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import no.nav.reops.project.ProjectEntity
import no.nav.reops.project.ProjectRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class DashboardServiceTest {

    @Mock
    private lateinit var dashboardRepository: DashboardRepository

    @Mock
    private lateinit var projectRepository: ProjectRepository

    @Mock
    private lateinit var changeService: ChangeService

    private lateinit var dashboardService: DashboardService

    @BeforeEach
    fun setUp() {
        dashboardService = DashboardService(dashboardRepository, projectRepository, changeService)
    }

    @Test
    fun `createDashboard saves entity and returns dto`() {
        val project = ProjectEntity(id = 1L, name = "project")
        val updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5)
        val savedEntity = DashboardEntity(
            id = 10L,
            project = project,
            name = "dashboard",
            description = "desc",
            updatedAt = updatedAt,
        )

        whenever(projectRepository.findById(1L)).thenReturn(Optional.of(project))
        whenever(dashboardRepository.existsByProjectIdAndName(1L, "dashboard")).thenReturn(false)
        whenever(dashboardRepository.save(any<DashboardEntity>())).thenReturn(savedEntity)

        val result = dashboardService.createDashboard(1L, CreateDashboardRequest("dashboard", "desc"))

        val captor = argumentCaptor<DashboardEntity>()
        verify(dashboardRepository).save(captor.capture())
        assertThat(captor.firstValue.name).isEqualTo("dashboard")
        assertThat(captor.firstValue.description).isEqualTo("desc")
        assertThat(captor.firstValue.project).isEqualTo(project)

        verify(changeService).createChange(10L, EntityType.DASHBOARD, ChangeType.CREATED)

        assertThat(result.id).isEqualTo(10L)
        assertThat(result.projectId).isEqualTo(1L)
        assertThat(result.name).isEqualTo("dashboard")
        assertThat(result.description).isEqualTo("desc")
        assertThat(result.updatedAt).isEqualTo(updatedAt)
    }

    @Test
    fun `createDashboard throws when project is missing`() {
        whenever(projectRepository.findById(99L)).thenReturn(Optional.empty())

        assertThatThrownBy {
            dashboardService.createDashboard(99L, CreateDashboardRequest("dashboard", null))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Project 99 not found")
    }

    @Test
    fun `createDashboard throws when name exists in project`() {
        val project = ProjectEntity(id = 1L, name = "project")
        whenever(projectRepository.findById(1L)).thenReturn(Optional.of(project))
        whenever(dashboardRepository.existsByProjectIdAndName(1L, "dashboard")).thenReturn(true)

        assertThatThrownBy {
            dashboardService.createDashboard(1L, CreateDashboardRequest("dashboard", null))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Dashboard with name 'dashboard' already exists in project 1")
    }

    @Test
    fun `getAllDashboards maps entities to dto`() {
        val project = ProjectEntity(id = 1L, name = "project")
        val first = DashboardEntity(
            id = 10L,
            project = project,
            name = "first",
            description = null,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        val second = DashboardEntity(
            id = 11L,
            project = project,
            name = "second",
            description = "desc",
            updatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6),
        )
        whenever(dashboardRepository.findAllByProjectId(1L)).thenReturn(listOf(first, second))

        val result = dashboardService.getAllDashboards(1L)

        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo(10L)
        assertThat(result[1].name).isEqualTo("second")
    }

    @Test
    fun `getDashboardById returns dto when found`() {
        val project = ProjectEntity(id = 1L, name = "project")
        val entity = DashboardEntity(
            id = 12L,
            project = project,
            name = "dashboard",
            description = null,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(dashboardRepository.findByIdAndProjectId(12L, 1L)).thenReturn(entity)

        val result = dashboardService.getDashboardById(1L, 12L)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(12L)
        assertThat(result.projectId).isEqualTo(1L)
    }

    @Test
    fun `getDashboardById returns null when missing`() {
        whenever(dashboardRepository.findByIdAndProjectId(12L, 1L)).thenReturn(null)

        val result = dashboardService.getDashboardById(1L, 12L)

        assertThat(result).isNull()
    }

    @Test
    fun `updateDashboard returns null when dashboard missing`() {
        whenever(dashboardRepository.findByIdAndProjectId(10L, 1L)).thenReturn(null)

        val result = dashboardService.updateDashboard(1L, 10L, UpdateDashboardRequest("name", null))

        assertThat(result).isNull()
    }

    @Test
    fun `updateDashboard throws when name exists in project`() {
        val project = ProjectEntity(id = 1L, name = "project")
        val entity = DashboardEntity(
            id = 10L,
            project = project,
            name = "old",
            description = null,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(dashboardRepository.findByIdAndProjectId(10L, 1L)).thenReturn(entity)
        whenever(dashboardRepository.existsByProjectIdAndName(1L, "new")).thenReturn(true)

        assertThatThrownBy {
            dashboardService.updateDashboard(1L, 10L, UpdateDashboardRequest("new", null))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Dashboard with name 'new' already exists in project 1")
    }

    @Test
    fun `updateDashboard saves changes and returns dto`() {
        val project = ProjectEntity(id = 1L, name = "project")
        val entity = DashboardEntity(
            id = 10L,
            project = project,
            name = "old",
            description = "old desc",
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(dashboardRepository.findByIdAndProjectId(10L, 1L)).thenReturn(entity)
        whenever(dashboardRepository.existsByProjectIdAndName(1L, "new")).thenReturn(false)
        whenever(dashboardRepository.save(any<DashboardEntity>())).thenReturn(entity)

        val result = dashboardService.updateDashboard(1L, 10L, UpdateDashboardRequest("new", "new desc"))

        val captor = argumentCaptor<DashboardEntity>()
        verify(dashboardRepository).save(captor.capture())
        assertThat(captor.firstValue.name).isEqualTo("new")
        assertThat(captor.firstValue.description).isEqualTo("new desc")

        verify(changeService).createChange(10L, EntityType.DASHBOARD, ChangeType.UPDATED)

        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo("new")
        assertThat(result.description).isEqualTo("new desc")
    }

    @Test
    fun `deleteDashboard returns false when missing`() {
        whenever(dashboardRepository.findByIdAndProjectId(10L, 1L)).thenReturn(null)

        val result = dashboardService.deleteDashboard(1L, 10L)

        assertThat(result).isFalse()
    }

    @Test
    fun `deleteDashboard deletes entity and records change`() {
        val project = ProjectEntity(id = 1L, name = "project")
        val entity = DashboardEntity(
            id = 10L,
            project = project,
            name = "dashboard",
            description = null,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(dashboardRepository.findByIdAndProjectId(10L, 1L)).thenReturn(entity)

        val result = dashboardService.deleteDashboard(1L, 10L)

        verify(dashboardRepository).delete(entity)
        verify(changeService).createChange(10L, EntityType.DASHBOARD, ChangeType.DELETED)
        assertThat(result).isTrue()
    }
}

