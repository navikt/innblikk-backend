package no.nav.reops.graphcategory

import no.nav.reops.change.ChangeDto
import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import no.nav.reops.dashboard.DashboardEntity
import no.nav.reops.dashboard.DashboardRepository
import no.nav.reops.project.ProjectEntity
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
class GraphCategoryServiceTest {

    @Mock
    private lateinit var graphCategoryRepository: GraphCategoryRepository

    @Mock
    private lateinit var dashboardRepository: DashboardRepository

    @Mock
    private lateinit var changeService: ChangeService

    private lateinit var graphCategoryService: GraphCategoryService

    @BeforeEach
    fun setUp() {
        graphCategoryService = GraphCategoryService(graphCategoryRepository, dashboardRepository, changeService)
    }

    @Test
    fun `createCategory saves entity and returns dto`() {
        val project = project(id = 1L)
        val dashboard = dashboard(project = project, id = 1L)
        val updatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6)
        val savedEntity = GraphCategoryEntity(
            id = 10L,
            dashboard = dashboard,
            name = "tab1",
            ordering = 0,
            updatedAt = updatedAt,
        )

        whenever(dashboardRepository.findById(1L)).thenReturn(Optional.of(dashboard))
        whenever(graphCategoryRepository.existsByDashboardIdAndName(1L, "tab1")).thenReturn(false)
        whenever(graphCategoryRepository.findMaxOrderingByDashboardId(1L)).thenReturn(-1)
        whenever(graphCategoryRepository.save(any<GraphCategoryEntity>())).thenReturn(savedEntity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.GRAPH_CATEGORY)).thenReturn(
            ChangeDto(
                entityId = 10L,
                entityType = EntityType.GRAPH_CATEGORY,
                changeType = ChangeType.CREATED,
                updatedAt = updatedAt,
                changedByEmail = "test@nav.no",
                changedByName = "Test User",
                changedByNavIdent = "T123456",
            )
        )

        val result = graphCategoryService.createCategory(1L, CreateGraphCategoryRequest("tab1"))

        val captor = argumentCaptor<GraphCategoryEntity>()
        verify(graphCategoryRepository).save(captor.capture())
        assertThat(captor.firstValue.name).isEqualTo("tab1")
        assertThat(captor.firstValue.ordering).isEqualTo(0)
        assertThat(captor.firstValue.dashboard).isEqualTo(dashboard)

        verify(changeService).createChange(10L, EntityType.GRAPH_CATEGORY, ChangeType.CREATED)

        assertThat(result.id).isEqualTo(10L)
        assertThat(result.dashboardId).isEqualTo(1L)
        assertThat(result.name).isEqualTo("tab1")
        assertThat(result.updatedAt).isEqualTo(updatedAt)
        assertThat(result.changedByEmail).isEqualTo("test@nav.no")
        assertThat(result.changedByName).isEqualTo("Test User")
        assertThat(result.changedByNavIdent).isEqualTo("T123456")
    }

    @Test
    fun `createCategory throws when dashboard is missing`() {
        whenever(dashboardRepository.findById(99L)).thenReturn(Optional.empty())

        assertThatThrownBy {
            graphCategoryService.createCategory(99L, CreateGraphCategoryRequest("tab1"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Dashboard 99 not found")
    }

    @Test
    fun `createCategory throws when name exists in dashboard`() {
        val project = project(id = 1L)
        val dashboard = dashboard(project = project, id = 1L)
        whenever(dashboardRepository.findById(1L)).thenReturn(Optional.of(dashboard))
        whenever(graphCategoryRepository.existsByDashboardIdAndName(1L, "tab1")).thenReturn(true)

        assertThatThrownBy {
            graphCategoryService.createCategory(1L, CreateGraphCategoryRequest("tab1"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Category with name 'tab1' already exists in dashboard 1")
    }

    @Test
    fun `getAllCategories maps entities to dto`() {
        val project = project(id = 1L)
        val dashboard = dashboard(project = project, id = 1L)
        val first = GraphCategoryEntity(
            id = 10L,
            dashboard = dashboard,
            name = "tab1",
            ordering = 0,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        val second = GraphCategoryEntity(
            id = 11L,
            dashboard = dashboard,
            name = "tab2",
            ordering = 1,
            updatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6),
        )
        whenever(graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(1L)).thenReturn(listOf(first, second))
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.GRAPH_CATEGORY)).thenReturn(null)
        whenever(changeService.getChangeByEntityIdAndEntityType(11L, EntityType.GRAPH_CATEGORY)).thenReturn(null)

        val result = graphCategoryService.getAllCategories(1L)

        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo(10L)
        assertThat(result[1].name).isEqualTo("tab2")
    }

    @Test
    fun `getCategoryById returns dto when found`() {
        val project = project(id = 1L)
        val dashboard = dashboard(project = project, id = 1L)
        val entity = GraphCategoryEntity(
            id = 12L,
            dashboard = dashboard,
            name = "tab1",
            ordering = 0,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(graphCategoryRepository.findByIdAndDashboardId(12L, 1L)).thenReturn(entity)
        whenever(changeService.getChangeByEntityIdAndEntityType(12L, EntityType.GRAPH_CATEGORY)).thenReturn(null)

        val result = graphCategoryService.getCategoryById(1L, 12L)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(12L)
        assertThat(result.dashboardId).isEqualTo(1L)
    }

    @Test
    fun `getCategoryById returns null when missing`() {
        whenever(graphCategoryRepository.findByIdAndDashboardId(12L, 1L)).thenReturn(null)

        val result = graphCategoryService.getCategoryById(1L, 12L)

        assertThat(result).isNull()
    }

    @Test
    fun `updateCategory returns null when category missing`() {
        whenever(graphCategoryRepository.findByIdAndDashboardId(10L, 1L)).thenReturn(null)

        val result = graphCategoryService.updateCategory(1L, 10L, UpdateGraphCategoryRequest("name"))

        assertThat(result).isNull()
    }

    @Test
    fun `updateCategory throws when name exists in dashboard`() {
        val project = project(id = 1L)
        val dashboard = dashboard(project = project, id = 1L)
        val entity = GraphCategoryEntity(
            id = 10L,
            dashboard = dashboard,
            name = "old",
            ordering = 0,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(graphCategoryRepository.findByIdAndDashboardId(10L, 1L)).thenReturn(entity)
        whenever(graphCategoryRepository.existsByDashboardIdAndName(1L, "new")).thenReturn(true)

        assertThatThrownBy {
            graphCategoryService.updateCategory(1L, 10L, UpdateGraphCategoryRequest("new"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Category with name 'new' already exists in dashboard 1")
    }

    @Test
    fun `updateCategory saves changes and returns dto`() {
        val project = project(id = 1L)
        val dashboard = dashboard(project = project, id = 1L)
        val entity = GraphCategoryEntity(
            id = 10L,
            dashboard = dashboard,
            name = "old",
            ordering = 0,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(graphCategoryRepository.findByIdAndDashboardId(10L, 1L)).thenReturn(entity)
        whenever(graphCategoryRepository.existsByDashboardIdAndName(1L, "new")).thenReturn(false)
        whenever(graphCategoryRepository.save(any<GraphCategoryEntity>())).thenReturn(entity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.GRAPH_CATEGORY)).thenReturn(null)

        val result = graphCategoryService.updateCategory(1L, 10L, UpdateGraphCategoryRequest("new"))

        val captor = argumentCaptor<GraphCategoryEntity>()
        verify(graphCategoryRepository).save(captor.capture())
        assertThat(captor.firstValue.name).isEqualTo("new")

        verify(changeService).createChange(10L, EntityType.GRAPH_CATEGORY, ChangeType.UPDATED)

        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo("new")
    }

    @Test
    fun `deleteCategory returns false when missing`() {
        whenever(graphCategoryRepository.findByIdAndDashboardId(10L, 1L)).thenReturn(null)

        val result = graphCategoryService.deleteCategory(1L, 10L)

        assertThat(result).isFalse()
    }

    @Test
    fun `deleteCategory deletes entity and records change`() {
        val project = project(id = 1L)
        val dashboard = dashboard(project = project, id = 1L)
        val entity = GraphCategoryEntity(
            id = 10L,
            dashboard = dashboard,
            name = "tab1",
            ordering = 0,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(graphCategoryRepository.findByIdAndDashboardId(10L, 1L)).thenReturn(entity)

        val result = graphCategoryService.deleteCategory(1L, 10L)

        verify(graphCategoryRepository).delete(entity)
        verify(changeService).createChange(10L, EntityType.GRAPH_CATEGORY, ChangeType.DELETED)
        assertThat(result).isTrue()
    }

    @Test
    fun `createCategory assigns next ordering value`() {
        val project = project(id = 1L)
        val dashboard = dashboard(project = project, id = 1L)
        val existingCategory = GraphCategoryEntity(
            id = 5L,
            dashboard = dashboard,
            name = "existing",
            ordering = 3,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        val savedEntity = GraphCategoryEntity(
            id = 10L,
            dashboard = dashboard,
            name = "new",
            ordering = 4,
            updatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6),
        )

        whenever(dashboardRepository.findById(1L)).thenReturn(Optional.of(dashboard))
        whenever(graphCategoryRepository.existsByDashboardIdAndName(1L, "new")).thenReturn(false)
        whenever(graphCategoryRepository.findMaxOrderingByDashboardId(1L)).thenReturn(3)
        whenever(graphCategoryRepository.save(any<GraphCategoryEntity>())).thenReturn(savedEntity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.GRAPH_CATEGORY)).thenReturn(null)

        graphCategoryService.createCategory(1L, CreateGraphCategoryRequest("new"))

        val captor = argumentCaptor<GraphCategoryEntity>()
        verify(graphCategoryRepository).save(captor.capture())
        assertThat(captor.firstValue.ordering).isEqualTo(4)
    }

    @Test
    fun `updateOrdering throws when dashboard not found`() {
        whenever(dashboardRepository.existsById(99L)).thenReturn(false)

        assertThatThrownBy {
            graphCategoryService.updateOrdering(99L, listOf(GraphCategoryOrderingEntry(1L, 0)))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Dashboard 99 not found")
    }

    @Test
    fun `updateOrdering throws when category not found in dashboard`() {
        whenever(dashboardRepository.existsById(1L)).thenReturn(true)
        whenever(graphCategoryRepository.findAllByDashboardIdAndIdIn(1L, listOf(99L))).thenReturn(emptyList())

        assertThatThrownBy {
            graphCategoryService.updateOrdering(1L, listOf(GraphCategoryOrderingEntry(99L, 0)))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Categories not found in dashboard 1: [99]")
    }


    @Test
    fun `updateCategory skips name uniqueness check when name unchanged`() {
        val project = project(id = 1L)
        val dashboard = dashboard(project = project, id = 1L)
        val entity = GraphCategoryEntity(
            id = 10L,
            dashboard = dashboard,
            name = "same",
            ordering = 0,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(graphCategoryRepository.findByIdAndDashboardId(10L, 1L)).thenReturn(entity)
        whenever(graphCategoryRepository.save(any<GraphCategoryEntity>())).thenReturn(entity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.GRAPH_CATEGORY)).thenReturn(null)

        val result = graphCategoryService.updateCategory(1L, 10L, UpdateGraphCategoryRequest("same"))

        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo("same")
        verify(changeService).createChange(10L, EntityType.GRAPH_CATEGORY, ChangeType.UPDATED)
    }

    @Test
    fun `getAllCategories returns empty list when no categories exist`() {
        whenever(graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(1L)).thenReturn(emptyList())

        val result = graphCategoryService.getAllCategories(1L)

        assertThat(result).isEmpty()
    }

    @Test
    fun `updateOrdering updates ordering and returns sorted result`() {
        val project = project(id = 1L)
        val dashboard = dashboard(project = project, id = 1L)
        val cat1 = GraphCategoryEntity(
            id = 10L,
            dashboard = dashboard,
            name = "first",
            ordering = 0,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        val cat2 = GraphCategoryEntity(
            id = 11L,
            dashboard = dashboard,
            name = "second",
            ordering = 1,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )

        whenever(dashboardRepository.existsById(1L)).thenReturn(true)
        whenever(graphCategoryRepository.findAllByDashboardIdAndIdIn(1L, listOf(10L, 11L)))
            .thenReturn(listOf(cat1, cat2))
        whenever(graphCategoryRepository.saveAll(any<Collection<GraphCategoryEntity>>()))
            .thenReturn(listOf(cat1, cat2))
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.GRAPH_CATEGORY)).thenReturn(null)
        whenever(changeService.getChangeByEntityIdAndEntityType(11L, EntityType.GRAPH_CATEGORY)).thenReturn(null)

        val entries = listOf(
            GraphCategoryOrderingEntry(10L, 2),
            GraphCategoryOrderingEntry(11L, 1),
        )
        val result = graphCategoryService.updateOrdering(1L, entries)

        assertThat(cat1.ordering).isEqualTo(2)
        assertThat(cat2.ordering).isEqualTo(1)
        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo(11L)
        assertThat(result[1].id).isEqualTo(10L)
    }

    private fun project(id: Long) = ProjectEntity(
        id = id,
        name = "project",
        description = null,
        updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
    )

    private fun dashboard(project: ProjectEntity, id: Long) = DashboardEntity(
        id = id,
        project = project,
        name = "dashboard",
        description = null,
        updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
    )
}

