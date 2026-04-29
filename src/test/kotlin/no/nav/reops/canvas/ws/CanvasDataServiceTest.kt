package no.nav.reops.canvas.ws

import no.nav.reops.change.ChangeDto
import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import no.nav.reops.dashboard.DashboardEntity
import no.nav.reops.dashboard.DashboardRepository
import no.nav.reops.graph.GraphEntity
import no.nav.reops.graph.GraphRepository
import no.nav.reops.graph.GraphType
import no.nav.reops.graphcategory.GraphCategoryEntity
import no.nav.reops.graphcategory.GraphCategoryRepository
import no.nav.reops.project.ProjectEntity
import no.nav.reops.query.QueryEntity
import no.nav.reops.query.QueryRepository
import no.nav.reops.security.AuthenticatedUser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
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
class CanvasDataServiceTest {

    @Mock
    private lateinit var graphCategoryRepository: GraphCategoryRepository

    @Mock
    private lateinit var graphRepository: GraphRepository

    @Mock
    private lateinit var queryRepository: QueryRepository

    @Mock
    private lateinit var dashboardRepository: DashboardRepository

    @Mock
    private lateinit var changeService: ChangeService

    @Mock
    private lateinit var dataSource: javax.sql.DataSource

    private lateinit var service: CanvasDataService

    private val updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5)
    private val testUser = AuthenticatedUser(
        email = "test@nav.no",
        name = "Test User",
        navIdent = "T123456",
    )
    private val changeDto = ChangeDto(
        entityId = 1L,
        entityType = EntityType.QUERY,
        changeType = ChangeType.CREATED,
        updatedAt = updatedAt,
    )

    @BeforeEach
    fun setUp() {
        service = CanvasDataService(graphCategoryRepository, graphRepository, queryRepository, dashboardRepository, changeService, dataSource)
    }

    // ── Test helpers ────────────────────────────────────────────────────────

    private fun project(id: Long = 1L) = ProjectEntity(
        id = id, name = "project", description = null, updatedAt = updatedAt,
    )

    private fun dashboard(id: Long = 1L, project: ProjectEntity = project()) = DashboardEntity(
        id = id, project = project, name = "dashboard", description = null, updatedAt = updatedAt,
    )

    private fun category(id: Long = 1L, dashboard: DashboardEntity = dashboard()) = GraphCategoryEntity(
        id = id, dashboard = dashboard, name = "category", ordering = 0, updatedAt = updatedAt,
    )

    private fun canvasGraph(
        id: Long = 10L,
        category: GraphCategoryEntity = category(),
        name: String = "canvas:sticky:abc",
        description: String = "[canvas]",
    ) = GraphEntity(
        id = id, category = category, name = name, description = description,
        graphType = GraphType.TEXT, width = 100, ordering = 0, updatedAt = updatedAt,
    )

    private fun query(
        id: Long = 100L,
        graph: GraphEntity = canvasGraph(),
        name: String = "canvas-config",
        sqlText: String = "SELECT '{\"kind\":\"sticky\"}' AS canvas_config",
        version: Long = 0,
    ) = QueryEntity(
        id = id, graph = graph, name = name, sqlText = sqlText, ordering = 0, version = version, updatedAt = updatedAt,
    )

    // ── getCanvasState ──────────────────────────────────────────────────────

    @Nested
    inner class GetCanvasState {

        @Test
        fun `returns empty state when no canvas graphs`() {
            val cat = category()
            whenever(graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(1L)).thenReturn(listOf(cat))
            whenever(graphRepository.findAllByCategoryIdOrderByOrderingAsc(1L)).thenReturn(emptyList())

            val result = service.getCanvasState(1L, 1L)

            assertThat(result["entries"] as List<*>).isEmpty()
        }

        @Test
        fun `returns canvas frames and skips non-canvas graphs`() {
            val cat = category()
            val canvasGraph = canvasGraph(id = 10L)
            val lineGraph = GraphEntity(
                id = 20L, category = cat, name = "line-graph", description = "some chart",
                graphType = GraphType.LINE, width = 50, ordering = 1, updatedAt = updatedAt,
            )
            val lockGraph = canvasGraph(id = 30L, description = "[canvas-lock]")

            whenever(graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(1L)).thenReturn(listOf(cat))
            whenever(graphRepository.findAllByCategoryIdOrderByOrderingAsc(1L))
                .thenReturn(listOf(canvasGraph, lineGraph, lockGraph))
            whenever(queryRepository.findAllByGraphIdOrderByOrderingAsc(10L))
                .thenReturn(listOf(query(graph = canvasGraph)))

            val result = service.getCanvasState(1L, 1L)

            @Suppress("UNCHECKED_CAST")
            val entries = result["entries"] as List<Map<String, Any?>>
            assertThat(entries).hasSize(1)
            assertThat((entries[0]["graph"] as Map<*, *>)["id"]).isEqualTo(10L)
        }
    }

    // ── saveFrame ───────────────────────────────────────────────────────────

    @Nested
    inner class SaveFrame {

        @Test
        fun `updates existing query and increments version`() {
            val graph = canvasGraph()
            val existingQuery = query(id = 100L, graph = graph, version = 3)

            whenever(queryRepository.findByIdAndGraphId(100L, 10L)).thenReturn(existingQuery)
            whenever(queryRepository.save(any<QueryEntity>())).thenAnswer { it.arguments[0] }
            whenever(changeService.createChange(any(), any(), any(), any())).thenReturn(changeDto)

            val msg = mapOf<String, Any?>(
                "graphId" to 10L, "queryId" to 100L,
                "sqlText" to "SELECT 'new' AS canvas_config",
                "version" to 3L,
            )

            val result = service.saveFrame(msg, testUser)

            assertThat(result["version"]).isEqualTo(4L)
            val captor = argumentCaptor<QueryEntity>()
            verify(queryRepository).save(captor.capture())
            assertThat(captor.firstValue.sqlText).isEqualTo("SELECT 'new' AS canvas_config")
            assertThat(captor.firstValue.version).isEqualTo(4L)
        }

        @Test
        fun `throws version conflict on mismatch`() {
            val graph = canvasGraph()
            val existingQuery = query(id = 100L, graph = graph, version = 5)

            whenever(queryRepository.findByIdAndGraphId(100L, 10L)).thenReturn(existingQuery)

            val msg = mapOf<String, Any?>(
                "graphId" to 10L, "queryId" to 100L,
                "sqlText" to "SELECT 'new' AS canvas_config",
                "version" to 3L,
            )

            assertThatThrownBy { service.saveFrame(msg, testUser) }
                .isInstanceOf(CanvasVersionConflictException::class.java)
        }

        @Test
        fun `creates new graph and query when categoryId and graphName given`() {
            val cat = category(id = 5L)

            whenever(graphCategoryRepository.findById(5L)).thenReturn(Optional.of(cat))
            whenever(graphRepository.findMaxOrderingByCategoryId(5L)).thenReturn(2)
            whenever(graphRepository.save(any<GraphEntity>())).thenAnswer {
                (it.arguments[0] as GraphEntity).also { g -> g.id = 50L }
            }
            whenever(queryRepository.save(any<QueryEntity>())).thenAnswer {
                (it.arguments[0] as QueryEntity).also { q -> q.id = 500L }
            }
            whenever(changeService.createChange(any(), any(), any(), any())).thenReturn(changeDto)

            val msg = mapOf<String, Any?>(
                "categoryId" to 5L,
                "graphName" to "canvas:sticky:new",
                "sqlText" to "SELECT '{}' AS canvas_config",
            )

            val result = service.saveFrame(msg, testUser)

            assertThat(result["graphId"]).isEqualTo(50L)
            assertThat(result["queryId"]).isEqualTo(500L)

            val graphCaptor = argumentCaptor<GraphEntity>()
            verify(graphRepository).save(graphCaptor.capture())
            assertThat(graphCaptor.firstValue.name).isEqualTo("canvas:sticky:new")
            assertThat(graphCaptor.firstValue.description).isEqualTo("[canvas]")
            assertThat(graphCaptor.firstValue.ordering).isEqualTo(3)
        }

        @Test
        fun `passes changedBy user to changeService`() {
            val graph = canvasGraph()
            val existingQuery = query(id = 100L, graph = graph, version = 0)

            whenever(queryRepository.findByIdAndGraphId(100L, 10L)).thenReturn(existingQuery)
            whenever(queryRepository.save(any<QueryEntity>())).thenAnswer { it.arguments[0] }
            whenever(changeService.createChange(any(), any(), any(), any())).thenReturn(changeDto)

            val msg = mapOf<String, Any?>(
                "graphId" to 10L, "queryId" to 100L,
                "sqlText" to "SELECT 'x' AS canvas_config",
                "version" to 0L,
            )

            service.saveFrame(msg, testUser)

            verify(changeService).createChange(100L, EntityType.QUERY, ChangeType.UPDATED, testUser)
        }

        @Test
        fun `throws when sqlText missing`() {
            val msg = mapOf<String, Any?>("graphId" to 10L, "queryId" to 100L)

            assertThatThrownBy { service.saveFrame(msg) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("sqlText")
        }
    }

    // ── deleteFrame ─────────────────────────────────────────────────────────

    @Nested
    inner class DeleteFrame {

        @Test
        fun `deletes graph and records change with user`() {
            val graph = canvasGraph(id = 10L)
            whenever(graphRepository.findByIdAndCategoryId(10L, 1L)).thenReturn(graph)
            whenever(changeService.createChange(any(), any(), any(), any())).thenReturn(changeDto)

            service.deleteFrame(1L, 10L, testUser)

            verify(graphRepository).delete(graph)
            verify(changeService).createChange(10L, EntityType.GRAPH, ChangeType.DELETED, testUser)
        }

        @Test
        fun `throws when graph not found`() {
            whenever(graphRepository.findByIdAndCategoryId(10L, 1L)).thenReturn(null)

            assertThatThrownBy { service.deleteFrame(1L, 10L) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    // ── Timer ───────────────────────────────────────────────────────────────

    @Nested
    inner class Timer {

        @Test
        fun `getTimerState returns null when no timer graph exists`() {
            whenever(graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(1L)).thenReturn(emptyList())

            val result = service.getTimerState(1L)

            assertThat(result).isNull()
        }

        @Test
        fun `getTimerState returns query map when timer exists`() {
            val cat = category()
            val graph = canvasGraph(id = 20L, name = "canvas:timer", description = "[canvas-timer]")
            val q = query(id = 200L, graph = graph, name = "canvas-timer", sqlText = "SELECT '{\"running\":true}' AS canvas_timer")

            whenever(graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(1L)).thenReturn(listOf(cat))
            whenever(graphRepository.findAllByCategoryIdOrderByOrderingAsc(1L)).thenReturn(listOf(graph))
            whenever(queryRepository.findAllByGraphIdOrderByOrderingAsc(20L)).thenReturn(listOf(q))

            val result = service.getTimerState(1L)

            assertThat(result).isNotNull
            assertThat(result!!["id"]).isEqualTo(200L)
            assertThat(result["sqlText"]).isEqualTo("SELECT '{\"running\":true}' AS canvas_timer")
        }

        @Test
        fun `saveTimerState updates existing timer query`() {
            val cat = category()
            val graph = canvasGraph(id = 20L, name = "canvas:timer", description = "[canvas-timer]")
            val q = query(id = 200L, graph = graph, name = "canvas-timer", version = 1)

            whenever(graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(1L)).thenReturn(listOf(cat))
            whenever(graphRepository.findAllByCategoryIdOrderByOrderingAsc(1L)).thenReturn(listOf(graph))
            whenever(queryRepository.findAllByGraphIdOrderByOrderingAsc(20L)).thenReturn(listOf(q))
            whenever(queryRepository.save(any<QueryEntity>())).thenAnswer { it.arguments[0] }
            whenever(changeService.createChange(any(), any(), any(), any())).thenReturn(changeDto)

            val payload = mapOf<String, Any?>("sqlText" to "SELECT 'new' AS canvas_timer")
            val result = service.saveTimerState(1L, payload, testUser)

            assertThat(result["version"]).isEqualTo(2L)
            verify(changeService).createChange(200L, EntityType.QUERY, ChangeType.UPDATED, testUser)
        }

        @Test
        fun `saveTimerState creates new timer when none exists`() {
            val cat = category()
            whenever(graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(1L)).thenReturn(listOf(cat))
            whenever(graphCategoryRepository.findById(1L)).thenReturn(Optional.of(cat))
            whenever(graphRepository.findAllByCategoryIdOrderByOrderingAsc(1L)).thenReturn(emptyList())
            whenever(graphRepository.findMaxOrderingByCategoryId(1L)).thenReturn(0)
            whenever(graphRepository.save(any<GraphEntity>())).thenAnswer {
                (it.arguments[0] as GraphEntity).also { g -> g.id = 30L }
            }
            whenever(queryRepository.save(any<QueryEntity>())).thenAnswer {
                (it.arguments[0] as QueryEntity).also { q -> q.id = 300L }
            }
            whenever(changeService.createChange(any(), any(), any(), any())).thenReturn(changeDto)

            val payload = mapOf<String, Any?>("sqlText" to "SELECT '{\"running\":true}' AS canvas_timer")
            val result = service.saveTimerState(1L, payload, testUser)

            assertThat(result["id"]).isEqualTo(300L)

            val graphCaptor = argumentCaptor<GraphEntity>()
            verify(graphRepository).save(graphCaptor.capture())
            assertThat(graphCaptor.firstValue.name).isEqualTo("canvas:timer")
            assertThat(graphCaptor.firstValue.description).isEqualTo("[canvas-timer]")
        }

        @Test
        fun `clearTimer deletes all timer graphs`() {
            val cat = category()
            val timerGraph = canvasGraph(id = 20L, name = "canvas:timer", description = "[canvas-timer]")
            val nonTimerGraph = canvasGraph(id = 21L, name = "canvas:sticky:x", description = "[canvas]")

            whenever(graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(1L)).thenReturn(listOf(cat))
            whenever(graphRepository.findAllByCategoryIdOrderByOrderingAsc(1L))
                .thenReturn(listOf(timerGraph, nonTimerGraph))
            whenever(changeService.createChange(any(), any(), any(), any())).thenReturn(changeDto)

            service.clearTimer(1L, testUser)

            verify(graphRepository).delete(timerGraph)
            verify(changeService).createChange(20L, EntityType.GRAPH, ChangeType.DELETED, testUser)
        }
    }

    // ── Dot Voting ──────────────────────────────────────────────────────────

    @Nested
    inner class DotVoting {

        @Test
        fun `getDotVotingState returns empty when no voting graphs exist`() {
            whenever(graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(1L)).thenReturn(emptyList())

            val result = service.getDotVotingState(1L)

            assertThat(result["session"]).isNull()
            @Suppress("UNCHECKED_CAST")
            assertThat(result["ballots"] as List<*>).isEmpty()
        }

        @Test
        fun `getDotVotingState returns session and ballots`() {
            val cat = category()
            val sessionGraph = canvasGraph(id = 40L, name = "canvas:dot-voting:session", description = "[canvas-dot-voting]")
            val ballotGraph = canvasGraph(id = 41L, name = "canvas:dot-voting:ballot:user1", description = "[canvas-dot-voting]")

            val sessionQuery = query(id = 400L, graph = sessionGraph, name = "canvas-dot-voting-session")
            val ballotQuery = query(id = 401L, graph = ballotGraph, name = "canvas-dot-voting-ballot")

            whenever(graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(1L)).thenReturn(listOf(cat))
            whenever(graphRepository.findAllByCategoryIdOrderByOrderingAsc(1L))
                .thenReturn(listOf(sessionGraph, ballotGraph))
            whenever(queryRepository.findAllByGraphIdOrderByOrderingAsc(40L)).thenReturn(listOf(sessionQuery))
            whenever(queryRepository.findAllByGraphIdOrderByOrderingAsc(41L)).thenReturn(listOf(ballotQuery))

            val result = service.getDotVotingState(1L)

            assertThat(result["session"]).isNotNull
            @Suppress("UNCHECKED_CAST")
            val ballots = result["ballots"] as List<Map<String, Any?>>
            assertThat(ballots).hasSize(1)
            assertThat(ballots[0]["id"]).isEqualTo(401L)
        }

        @Test
        fun `saveDotVotingSession updates existing session`() {
            val cat = category()
            val graph = canvasGraph(id = 40L, name = "canvas:dot-voting:session", description = "[canvas-dot-voting]")
            val q = query(id = 400L, graph = graph, name = "canvas-dot-voting-session", version = 2)

            whenever(graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(1L)).thenReturn(listOf(cat))
            whenever(graphRepository.findAllByCategoryIdOrderByOrderingAsc(1L)).thenReturn(listOf(graph))
            whenever(queryRepository.findAllByGraphIdOrderByOrderingAsc(40L)).thenReturn(listOf(q))
            whenever(queryRepository.save(any<QueryEntity>())).thenAnswer { it.arguments[0] }
            whenever(changeService.createChange(any(), any(), any(), any())).thenReturn(changeDto)

            val payload = mapOf<String, Any?>("sqlText" to "SELECT 'new' AS x")
            service.saveDotVotingSession(1L, payload, testUser)

            verify(changeService).createChange(400L, EntityType.QUERY, ChangeType.UPDATED, testUser)
        }

        @Test
        fun `saveDotVotingBallot creates new ballot`() {
            val cat = category()
            whenever(graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(1L)).thenReturn(listOf(cat))
            whenever(graphCategoryRepository.findById(1L)).thenReturn(Optional.of(cat))
            whenever(graphRepository.findAllByCategoryIdOrderByOrderingAsc(1L)).thenReturn(emptyList())
            whenever(graphRepository.findMaxOrderingByCategoryId(1L)).thenReturn(0)
            whenever(graphRepository.save(any<GraphEntity>())).thenAnswer {
                (it.arguments[0] as GraphEntity).also { g -> g.id = 50L }
            }
            whenever(queryRepository.save(any<QueryEntity>())).thenAnswer {
                (it.arguments[0] as QueryEntity).also { q -> q.id = 500L }
            }
            whenever(changeService.createChange(any(), any(), any(), any())).thenReturn(changeDto)

            val payload = mapOf<String, Any?>("sqlText" to "SELECT '{\"votes\":[1,2]}' AS x")
            val result = service.saveDotVotingBallot(1L, "user123", payload, testUser)

            assertThat(result["id"]).isEqualTo(500L)

            val graphCaptor = argumentCaptor<GraphEntity>()
            verify(graphRepository).save(graphCaptor.capture())
            assertThat(graphCaptor.firstValue.name).isEqualTo("canvas:dot-voting:ballot:user123")
            assertThat(graphCaptor.firstValue.description).isEqualTo("[canvas-dot-voting]")
        }

        @Test
        fun `clearDotVoting deletes all dot voting graphs`() {
            val cat = category()
            val sessionGraph = canvasGraph(id = 40L, name = "canvas:dot-voting:session", description = "[canvas-dot-voting]")
            val ballotGraph = canvasGraph(id = 41L, name = "canvas:dot-voting:ballot:u1", description = "[canvas-dot-voting]")
            val canvasFrame = canvasGraph(id = 42L, name = "canvas:sticky:x", description = "[canvas]")

            whenever(graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(1L)).thenReturn(listOf(cat))
            whenever(graphRepository.findAllByCategoryIdOrderByOrderingAsc(1L))
                .thenReturn(listOf(sessionGraph, ballotGraph, canvasFrame))
            whenever(changeService.createChange(any(), any(), any(), any())).thenReturn(changeDto)

            service.clearDotVoting(1L, testUser)

            verify(graphRepository).delete(sessionGraph)
            verify(graphRepository).delete(ballotGraph)
        }
    }
}
