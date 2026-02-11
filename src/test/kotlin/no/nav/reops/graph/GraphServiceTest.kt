package no.nav.reops.graph

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
class GraphServiceTest {

    @Mock
    private lateinit var graphRepository: GraphRepository

    @Mock
    private lateinit var dashboardRepository: DashboardRepository

    @Mock
    private lateinit var changeService: ChangeService

    private lateinit var graphService: GraphService

    @BeforeEach
    fun setUp() {
        graphService = GraphService(graphRepository, dashboardRepository, changeService)
    }

    @Test
    fun `createGraph saves entity and returns dto`() {
        val project = project(id = 1L)
        val dashboard = dashboard(project = project, id = 1L)
        val updatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6)
        val savedEntity = GraphEntity(
            id = 10L,
            dashboard = dashboard,
            name = "graph",
            graphType = "line",
            updatedAt = updatedAt,
        )

        whenever(dashboardRepository.findById(1L)).thenReturn(Optional.of(dashboard))
        whenever(graphRepository.existsByDashboardIdAndName(1L, "graph")).thenReturn(false)
        whenever(graphRepository.save(any<GraphEntity>())).thenReturn(savedEntity)

        val result = graphService.createGraph(1L, CreateGraphRequest("graph", "line"))

        val captor = argumentCaptor<GraphEntity>()
        verify(graphRepository).save(captor.capture())
        assertThat(captor.firstValue.name).isEqualTo("graph")
        assertThat(captor.firstValue.graphType).isEqualTo("line")
        assertThat(captor.firstValue.dashboard).isEqualTo(dashboard)

        verify(changeService).createChange(10L, EntityType.GRAPH, ChangeType.CREATED)

        assertThat(result.id).isEqualTo(10L)
        assertThat(result.dashboardId).isEqualTo(1L)
        assertThat(result.name).isEqualTo("graph")
        assertThat(result.graphType).isEqualTo("line")
        assertThat(result.updatedAt).isEqualTo(updatedAt)
    }

    @Test
    fun `createGraph throws when dashboard is missing`() {
        whenever(dashboardRepository.findById(99L)).thenReturn(Optional.empty())

        assertThatThrownBy {
            graphService.createGraph(99L, CreateGraphRequest("graph", null))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Dashboard 99 not found")
    }

    @Test
    fun `createGraph throws when name exists in dashboard`() {
        val project = project(id = 1L)
        val dashboard = dashboard(project = project, id = 1L)
        whenever(dashboardRepository.findById(1L)).thenReturn(Optional.of(dashboard))
        whenever(graphRepository.existsByDashboardIdAndName(1L, "graph")).thenReturn(true)

        assertThatThrownBy {
            graphService.createGraph(1L, CreateGraphRequest("graph", null))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Graph with name 'graph' already exists in dashboard 1")
    }

    @Test
    fun `getAllGraphs maps entities to dto`() {
        val project = project(id = 1L)
        val dashboard = dashboard(project = project, id = 1L)
        val first = GraphEntity(
            id = 10L,
            dashboard = dashboard,
            name = "first",
            graphType = "line",
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        val second = GraphEntity(
            id = 11L,
            dashboard = dashboard,
            name = "second",
            graphType = null,
            updatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6),
        )
        whenever(graphRepository.findAllByDashboardId(1L)).thenReturn(listOf(first, second))

        val result = graphService.getAllGraphs(1L)

        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo(10L)
        assertThat(result[1].name).isEqualTo("second")
    }

    @Test
    fun `getGraphById returns dto when found`() {
        val project = project(id = 1L)
        val dashboard = dashboard(project = project, id = 1L)
        val entity = GraphEntity(
            id = 12L,
            dashboard = dashboard,
            name = "graph",
            graphType = "bar",
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(graphRepository.findByIdAndDashboardId(12L, 1L)).thenReturn(entity)

        val result = graphService.getGraphById(1L, 12L)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(12L)
        assertThat(result.dashboardId).isEqualTo(1L)
    }

    @Test
    fun `getGraphById returns null when missing`() {
        whenever(graphRepository.findByIdAndDashboardId(12L, 1L)).thenReturn(null)

        val result = graphService.getGraphById(1L, 12L)

        assertThat(result).isNull()
    }

    @Test
    fun `updateGraph returns null when graph missing`() {
        whenever(graphRepository.findByIdAndDashboardId(10L, 1L)).thenReturn(null)

        val result = graphService.updateGraph(1L, 10L, UpdateGraphRequest("name", null))

        assertThat(result).isNull()
    }

    @Test
    fun `updateGraph throws when name exists in dashboard`() {
        val project = project(id = 1L)
        val dashboard = dashboard(project = project, id = 1L)
        val entity = GraphEntity(
            id = 10L,
            dashboard = dashboard,
            name = "old",
            graphType = null,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(graphRepository.findByIdAndDashboardId(10L, 1L)).thenReturn(entity)
        whenever(graphRepository.existsByDashboardIdAndName(1L, "new")).thenReturn(true)

        assertThatThrownBy {
            graphService.updateGraph(1L, 10L, UpdateGraphRequest("new", null))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Graph with name 'new' already exists in dashboard 1")
    }

    @Test
    fun `updateGraph saves changes and returns dto`() {
        val project = project(id = 1L)
        val dashboard = dashboard(project = project, id = 1L)
        val entity = GraphEntity(
            id = 10L,
            dashboard = dashboard,
            name = "old",
            graphType = "bar",
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(graphRepository.findByIdAndDashboardId(10L, 1L)).thenReturn(entity)
        whenever(graphRepository.existsByDashboardIdAndName(1L, "new")).thenReturn(false)
        whenever(graphRepository.save(any<GraphEntity>())).thenReturn(entity)

        val result = graphService.updateGraph(1L, 10L, UpdateGraphRequest("new", "line"))

        val captor = argumentCaptor<GraphEntity>()
        verify(graphRepository).save(captor.capture())
        assertThat(captor.firstValue.name).isEqualTo("new")
        assertThat(captor.firstValue.graphType).isEqualTo("line")

        verify(changeService).createChange(10L, EntityType.GRAPH, ChangeType.UPDATED)

        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo("new")
        assertThat(result.graphType).isEqualTo("line")
    }

    @Test
    fun `deleteGraph returns false when missing`() {
        whenever(graphRepository.findByIdAndDashboardId(10L, 1L)).thenReturn(null)

        val result = graphService.deleteGraph(1L, 10L)

        assertThat(result).isFalse()
    }

    @Test
    fun `deleteGraph deletes entity and records change`() {
        val project = project(id = 1L)
        val dashboard = dashboard(project = project, id = 1L)
        val entity = GraphEntity(
            id = 10L,
            dashboard = dashboard,
            name = "graph",
            graphType = null,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(graphRepository.findByIdAndDashboardId(10L, 1L)).thenReturn(entity)

        val result = graphService.deleteGraph(1L, 10L)

        verify(graphRepository).delete(entity)
        verify(changeService).createChange(10L, EntityType.GRAPH, ChangeType.DELETED)
        assertThat(result).isTrue()
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
