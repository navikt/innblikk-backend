package no.nav.reops.project

import no.nav.reops.change.ChangeDto
import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class ProjectServiceTest {

    @Mock
    private lateinit var projectRepository: ProjectRepository

    @Mock
    private lateinit var changeService: ChangeService

    private lateinit var projectService: ProjectService

    @BeforeEach
    fun setUp() {
        projectService = ProjectService(projectRepository, changeService)
    }

    @Test
    fun `createProject saves entity and returns dto`() {
        val updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5)
        val savedEntity = ProjectEntity(
            id = 1L,
            name = "project",
            description = "desc",
            updatedAt = updatedAt,
        )

        whenever(projectRepository.save(any<ProjectEntity>())).thenReturn(savedEntity)
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.PROJECT)).thenReturn(
            ChangeDto(
                entityId = 1L,
                entityType = EntityType.PROJECT,
                changeType = ChangeType.CREATED,
                updatedAt = updatedAt,
                changedByEmail = "test@nav.no",
                changedByName = "Test User",
                changedByNavIdent = "T123456",
            )
        )

        val result = projectService.createProject(CreateProjectRequest("project", "desc"))

        val captor = argumentCaptor<ProjectEntity>()
        verify(projectRepository).save(captor.capture())
        assertThat(captor.firstValue.name).isEqualTo("project")
        assertThat(captor.firstValue.description).isEqualTo("desc")

        verify(changeService).createChange(1L, EntityType.PROJECT, ChangeType.CREATED)

        assertThat(result.id).isEqualTo(1L)
        assertThat(result.name).isEqualTo("project")
        assertThat(result.description).isEqualTo("desc")
        assertThat(result.updatedAt).isEqualTo(updatedAt)
        assertThat(result.changedByEmail).isEqualTo("test@nav.no")
        assertThat(result.changedByName).isEqualTo("Test User")
        assertThat(result.changedByNavIdent).isEqualTo("T123456")
    }

    @Test
    fun `getAllProjects maps entities to dto`() {
        val first = ProjectEntity(
            id = 1L,
            name = "first",
            description = null,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        val second = ProjectEntity(
            id = 2L,
            name = "second",
            description = "desc",
            updatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6),
        )
        whenever(projectRepository.findAll()).thenReturn(listOf(first, second))
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.PROJECT)).thenReturn(null)
        whenever(changeService.getChangeByEntityIdAndEntityType(2L, EntityType.PROJECT)).thenReturn(null)

        val result = projectService.getAllProjects()

        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo(1L)
        assertThat(result[1].name).isEqualTo("second")
    }

    @Test
    fun `getProjectById returns dto when found`() {
        val entity = ProjectEntity(
            id = 1L,
            name = "project",
            description = "desc",
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(projectRepository.findById(1L)).thenReturn(Optional.of(entity))
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.PROJECT)).thenReturn(null)

        val result = projectService.getProjectById(1L)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(1L)
        assertThat(result.name).isEqualTo("project")
    }

    @Test
    fun `getProjectById returns null when missing`() {
        whenever(projectRepository.findById(99L)).thenReturn(Optional.empty())

        val result = projectService.getProjectById(99L)

        assertThat(result).isNull()
    }

    @Test
    fun `updateProject returns null when project missing`() {
        whenever(projectRepository.findById(10L)).thenReturn(Optional.empty())

        val result = projectService.updateProject(10L, UpdateProjectRequest("name", "desc"))

        assertThat(result).isNull()
        verifyNoInteractions(changeService)
    }

    @Test
    fun `updateProject saves changes and returns dto`() {
        val entity = ProjectEntity(
            id = 10L,
            name = "old",
            description = "old desc",
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(projectRepository.findById(10L)).thenReturn(Optional.of(entity))
        whenever(projectRepository.save(any<ProjectEntity>())).thenReturn(entity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.PROJECT)).thenReturn(null)

        val result = projectService.updateProject(10L, UpdateProjectRequest("new", "new desc"))

        val captor = argumentCaptor<ProjectEntity>()
        verify(projectRepository).save(captor.capture())
        assertThat(captor.firstValue.name).isEqualTo("new")
        assertThat(captor.firstValue.description).isEqualTo("new desc")

        verify(changeService).createChange(10L, EntityType.PROJECT, ChangeType.UPDATED)

        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo("new")
        assertThat(result.description).isEqualTo("new desc")
    }

    @Test
    fun `deleteProject returns false when missing`() {
        whenever(projectRepository.existsById(10L)).thenReturn(false)

        val result = projectService.deleteProject(10L)

        assertThat(result).isFalse()
        verifyNoInteractions(changeService)
    }

    @Test
    fun `deleteProject deletes entity and records change`() {
        whenever(projectRepository.existsById(10L)).thenReturn(true)

        val result = projectService.deleteProject(10L)

        verify(projectRepository).deleteById(10L)
        verify(changeService).createChange(10L, EntityType.PROJECT, ChangeType.DELETED)
        assertThat(result).isTrue()
    }

    @Test
    fun `createProject with null description`() {
        val savedEntity = ProjectEntity(
            id = 1L,
            name = "project",
            description = null,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )

        whenever(projectRepository.save(any<ProjectEntity>())).thenReturn(savedEntity)
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.PROJECT)).thenReturn(null)

        val result = projectService.createProject(CreateProjectRequest("project", null))

        val captor = argumentCaptor<ProjectEntity>()
        verify(projectRepository).save(captor.capture())
        assertThat(captor.firstValue.description).isNull()
        assertThat(result.description).isNull()
    }

    @Test
    fun `getAllProjects returns empty list when no projects exist`() {
        whenever(projectRepository.findAll()).thenReturn(emptyList())

        val result = projectService.getAllProjects()

        assertThat(result).isEmpty()
    }
}

