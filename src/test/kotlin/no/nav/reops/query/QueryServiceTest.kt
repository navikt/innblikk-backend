package no.nav.reops.query

import no.nav.reops.change.ChangeDto
import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import no.nav.reops.dashboard.DashboardEntity
import no.nav.reops.graph.GraphEntity
import no.nav.reops.graph.GraphRepository
import no.nav.reops.graph.GraphType
import no.nav.reops.graphcategory.GraphCategoryEntity
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
class QueryServiceTest {

    @Mock
    private lateinit var queryRepository: QueryRepository

    @Mock
    private lateinit var graphRepository: GraphRepository

    @Mock
    private lateinit var changeService: ChangeService

    private lateinit var queryService: QueryService

    @BeforeEach
    fun setUp() {
        queryService = QueryService(queryRepository, graphRepository, changeService)
    }

    @Test
    fun `createQuery saves entity and returns dto`() {
        val graph = graph(id = 1L)
        val updatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6)
        val savedEntity = QueryEntity(
            id = 10L,
            graph = graph,
            name = "query",
            sqlText = "select 1",
            updatedAt = updatedAt,
        )

        whenever(graphRepository.findById(1L)).thenReturn(Optional.of(graph))
        whenever(queryRepository.existsByGraphIdAndName(1L, "query")).thenReturn(false)
        whenever(queryRepository.findMaxOrderingByGraphId(1L)).thenReturn(-1)
        whenever(queryRepository.save(any<QueryEntity>())).thenReturn(savedEntity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.QUERY)).thenReturn(
            ChangeDto(
                entityId = 10L,
                entityType = EntityType.QUERY,
                changeType = ChangeType.CREATED,
                updatedAt = updatedAt,
                changedByEmail = "test@nav.no",
                changedByName = "Test User",
                changedByNavIdent = "T123456",
            )
        )

        val result = queryService.createQuery(1L, CreateQueryRequest("query", "select 1"))

        val captor = argumentCaptor<QueryEntity>()
        verify(queryRepository).save(captor.capture())
        assertThat(captor.firstValue.name).isEqualTo("query")
        assertThat(captor.firstValue.sqlText).isEqualTo("select 1")
        assertThat(captor.firstValue.graph).isEqualTo(graph)
        assertThat(captor.firstValue.ordering).isEqualTo(0)

        verify(changeService).createChange(10L, EntityType.QUERY, ChangeType.CREATED)

        assertThat(result.id).isEqualTo(10L)
        assertThat(result.graphId).isEqualTo(1L)
        assertThat(result.name).isEqualTo("query")
        assertThat(result.sqlText).isEqualTo("select 1")
        assertThat(result.updatedAt).isEqualTo(updatedAt)
        assertThat(result.changedByEmail).isEqualTo("test@nav.no")
        assertThat(result.changedByName).isEqualTo("Test User")
        assertThat(result.changedByNavIdent).isEqualTo("T123456")
    }

    @Test
    fun `createQuery throws when graph is missing`() {
        whenever(graphRepository.findById(99L)).thenReturn(Optional.empty())

        assertThatThrownBy {
            queryService.createQuery(99L, CreateQueryRequest("query", "select 1"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Graph 99 not found")
    }

    @Test
    fun `createQuery throws when name exists in graph`() {
        val graph = graph(id = 1L)
        whenever(graphRepository.findById(1L)).thenReturn(Optional.of(graph))
        whenever(queryRepository.existsByGraphIdAndName(1L, "query")).thenReturn(true)

        assertThatThrownBy {
            queryService.createQuery(1L, CreateQueryRequest("query", "select 1"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Graph with name 'query' already exists in graph 1")
    }

    @Test
    fun `createQuery throws when sql is not select`() {
        assertThatThrownBy {
            queryService.createQuery(1L, CreateQueryRequest("query", "update users set a=1"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Only SELECT queries are allowed")
    }

    @Test
    fun `createQuery throws when sql contains multiple statements`() {
        assertThatThrownBy {
            queryService.createQuery(1L, CreateQueryRequest("query", "select 1; drop table x"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Only single-statement SELECT queries are allowed")
    }

    @Test
    fun `createQuery throws when sql is blank`() {
        assertThatThrownBy {
            queryService.createQuery(1L, CreateQueryRequest("query", ""))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Query sqlText must not be blank")
    }

    @Test
    fun `createQuery throws when sql is whitespace only`() {
        assertThatThrownBy {
            queryService.createQuery(1L, CreateQueryRequest("query", "   "))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Query sqlText must not be blank")
    }

    @Test
    fun `createQuery throws when sql is insert`() {
        assertThatThrownBy {
            queryService.createQuery(1L, CreateQueryRequest("query", "insert into users values (1)"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Only SELECT queries are allowed")
    }

    @Test
    fun `createQuery throws when sql is delete`() {
        assertThatThrownBy {
            queryService.createQuery(1L, CreateQueryRequest("query", "delete from users"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Only SELECT queries are allowed")
    }

    @Test
    fun `createQuery throws when sql is drop table`() {
        assertThatThrownBy {
            queryService.createQuery(1L, CreateQueryRequest("query", "drop table users"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Only SELECT queries are allowed")
    }

    @Test
    fun `createQuery allows uppercase SELECT`() {
        val graph = graph(id = 1L)
        val savedEntity = QueryEntity(
            id = 10L,
            graph = graph,
            name = "query",
            sqlText = "SELECT 1",
            updatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6),
        )

        whenever(graphRepository.findById(1L)).thenReturn(Optional.of(graph))
        whenever(queryRepository.existsByGraphIdAndName(1L, "query")).thenReturn(false)
        whenever(queryRepository.findMaxOrderingByGraphId(1L)).thenReturn(-1)
        whenever(queryRepository.save(any<QueryEntity>())).thenReturn(savedEntity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.QUERY)).thenReturn(null)

        val result = queryService.createQuery(1L, CreateQueryRequest("query", "SELECT 1"))

        assertThat(result.sqlText).isEqualTo("SELECT 1")
    }

    @Test
    fun `createQuery allows uppercase WITH cte`() {
        val graph = graph(id = 1L)
        val savedEntity = QueryEntity(
            id = 10L,
            graph = graph,
            name = "query",
            sqlText = "WITH cte AS (SELECT 1) SELECT * FROM cte",
            updatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6),
        )

        whenever(graphRepository.findById(1L)).thenReturn(Optional.of(graph))
        whenever(queryRepository.existsByGraphIdAndName(1L, "query")).thenReturn(false)
        whenever(queryRepository.findMaxOrderingByGraphId(1L)).thenReturn(-1)
        whenever(queryRepository.save(any<QueryEntity>())).thenReturn(savedEntity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.QUERY)).thenReturn(null)

        val result = queryService.createQuery(
            1L,
            CreateQueryRequest("query", "WITH cte AS (SELECT 1) SELECT * FROM cte"),
        )

        assertThat(result.sqlText).isEqualTo("WITH cte AS (SELECT 1) SELECT * FROM cte")
    }

    @Test
    fun `createQuery allows leading whitespace before select`() {
        val graph = graph(id = 1L)
        val savedEntity = QueryEntity(
            id = 10L,
            graph = graph,
            name = "query",
            sqlText = "  select 1",
            updatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6),
        )

        whenever(graphRepository.findById(1L)).thenReturn(Optional.of(graph))
        whenever(queryRepository.existsByGraphIdAndName(1L, "query")).thenReturn(false)
        whenever(queryRepository.findMaxOrderingByGraphId(1L)).thenReturn(-1)
        whenever(queryRepository.save(any<QueryEntity>())).thenReturn(savedEntity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.QUERY)).thenReturn(null)

        val result = queryService.createQuery(1L, CreateQueryRequest("query", "  select 1"))

        assertThat(result).isNotNull
    }

    @Test
    fun `updateQuery throws when sql is blank`() {
        assertThatThrownBy {
            queryService.updateQuery(1L, 10L, UpdateQueryRequest("name", ""))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Query sqlText must not be blank")
    }

    @Test
    fun `updateQuery throws when sql contains semicolons`() {
        assertThatThrownBy {
            queryService.updateQuery(1L, 10L, UpdateQueryRequest("name", "select 1; drop table x"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Only single-statement SELECT queries are allowed")
    }

    @Test
    fun `updateQuery skips name uniqueness check when name unchanged`() {
        val graph = graph(id = 1L)
        val entity = QueryEntity(
            id = 10L,
            graph = graph,
            name = "same",
            sqlText = "select 1",
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(queryRepository.findByIdAndGraphId(10L, 1L)).thenReturn(entity)
        whenever(queryRepository.save(any<QueryEntity>())).thenReturn(entity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.QUERY)).thenReturn(null)

        val result = queryService.updateQuery(1L, 10L, UpdateQueryRequest("same", "select 2"))

        assertThat(result).isNotNull
        assertThat(result!!.sqlText).isEqualTo("select 2")
    }

    @Test
    fun `getAllQuery maps entities to dto`() {
        val graph = graph(id = 1L)
        val first = QueryEntity(
            id = 10L,
            graph = graph,
            name = "first",
            sqlText = "select 1",
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        val second = QueryEntity(
            id = 11L,
            graph = graph,
            name = "second",
            sqlText = "select 2",
            updatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6),
        )
        whenever(queryRepository.findAllByGraphIdOrderByOrderingAsc(1L)).thenReturn(listOf(first, second))
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.QUERY)).thenReturn(null)
        whenever(changeService.getChangeByEntityIdAndEntityType(11L, EntityType.QUERY)).thenReturn(null)

        val result = queryService.getAllQuery(1L)

        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo(10L)
        assertThat(result[1].name).isEqualTo("second")
    }

    @Test
    fun `getQueryById returns dto when found`() {
        val graph = graph(id = 1L)
        val entity = QueryEntity(
            id = 12L,
            graph = graph,
            name = "query",
            sqlText = "select 1",
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(queryRepository.findByIdAndGraphId(12L, 1L)).thenReturn(entity)
        whenever(changeService.getChangeByEntityIdAndEntityType(12L, EntityType.QUERY)).thenReturn(null)

        val result = queryService.getQueryById(1L, 12L)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(12L)
        assertThat(result.graphId).isEqualTo(1L)
    }

    @Test
    fun `getQueryById returns null when missing`() {
        whenever(queryRepository.findByIdAndGraphId(12L, 1L)).thenReturn(null)

        val result = queryService.getQueryById(1L, 12L)

        assertThat(result).isNull()
    }

    @Test
    fun `updateQuery returns null when query missing`() {
        whenever(queryRepository.findByIdAndGraphId(10L, 1L)).thenReturn(null)

        val result = queryService.updateQuery(1L, 10L, UpdateQueryRequest("name", "select 1"))

        assertThat(result).isNull()
    }

    @Test
    fun `updateQuery throws when name exists in graph`() {
        val graph = graph(id = 1L)
        val entity = QueryEntity(
            id = 10L,
            graph = graph,
            name = "old",
            sqlText = "select 1",
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(queryRepository.findByIdAndGraphId(10L, 1L)).thenReturn(entity)
        whenever(queryRepository.existsByGraphIdAndName(1L, "new")).thenReturn(true)

        assertThatThrownBy {
            queryService.updateQuery(1L, 10L, UpdateQueryRequest("new", "select 1"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Graph with name 'new' already exists in graph 1")
    }

    @Test
    fun `updateQuery throws when sql is not select`() {
        assertThatThrownBy {
            queryService.updateQuery(1L, 10L, UpdateQueryRequest("old", "delete from users"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Only SELECT queries are allowed")
    }

    @Test
    fun `updateQuery saves changes and returns dto`() {
        val graph = graph(id = 1L)
        val entity = QueryEntity(
            id = 10L,
            graph = graph,
            name = "old",
            sqlText = "select 1",
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(queryRepository.findByIdAndGraphId(10L, 1L)).thenReturn(entity)
        whenever(queryRepository.existsByGraphIdAndName(1L, "new")).thenReturn(false)
        whenever(queryRepository.save(any<QueryEntity>())).thenReturn(entity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.QUERY)).thenReturn(null)

        val result = queryService.updateQuery(1L, 10L, UpdateQueryRequest("new", "select 2"))

        val captor = argumentCaptor<QueryEntity>()
        verify(queryRepository).save(captor.capture())
        assertThat(captor.firstValue.name).isEqualTo("new")
        assertThat(captor.firstValue.sqlText).isEqualTo("select 2")

        verify(changeService).createChange(10L, EntityType.QUERY, ChangeType.UPDATED)

        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo("new")
        assertThat(result.sqlText).isEqualTo("select 2")
    }

    @Test
    fun `deleteQuery returns false when missing`() {
        whenever(queryRepository.findByIdAndGraphId(10L, 1L)).thenReturn(null)

        val result = queryService.deleteQuery(1L, 10L)

        assertThat(result).isFalse()
    }

    @Test
    fun `deleteQuery deletes entity and records change`() {
        val graph = graph(id = 1L)
        val entity = QueryEntity(
            id = 10L,
            graph = graph,
            name = "query",
            sqlText = "select 1",
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(queryRepository.findByIdAndGraphId(10L, 1L)).thenReturn(entity)

        val result = queryService.deleteQuery(1L, 10L)

        verify(queryRepository).delete(entity)
        verify(changeService).createChange(10L, EntityType.QUERY, ChangeType.DELETED)
        assertThat(result).isTrue()
    }

    @Test
    fun `createQuery allows with cte select`() {
        val graph = graph(id = 1L)
        val updatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6)
        val savedEntity = QueryEntity(
            id = 10L,
            graph = graph,
            name = "query",
            sqlText = "with cte as (select 1 as a) select a from cte",
            updatedAt = updatedAt,
        )

        whenever(graphRepository.findById(1L)).thenReturn(Optional.of(graph))
        whenever(queryRepository.existsByGraphIdAndName(1L, "query")).thenReturn(false)
        whenever(queryRepository.findMaxOrderingByGraphId(1L)).thenReturn(-1)
        whenever(queryRepository.save(any<QueryEntity>())).thenReturn(savedEntity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.QUERY)).thenReturn(null)

        val result = queryService.createQuery(
            1L,
            CreateQueryRequest("query", "with cte as (select 1 as a) select a from cte"),
        )

        assertThat(result.sqlText).isEqualTo("with cte as (select 1 as a) select a from cte")
    }

    @Test
    fun `updateQuery allows with cte select`() {
        val graph = graph(id = 1L)
        val entity = QueryEntity(
            id = 10L,
            graph = graph,
            name = "old",
            sqlText = "select 1",
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(queryRepository.findByIdAndGraphId(10L, 1L)).thenReturn(entity)
        whenever(queryRepository.save(any<QueryEntity>())).thenReturn(entity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.QUERY)).thenReturn(null)

        val result = queryService.updateQuery(
            1L,
            10L,
            UpdateQueryRequest("old", "with cte as (select 1 as a) select a from cte"),
        )

        assertThat(result).isNotNull
        assertThat(result!!.sqlText).isEqualTo("with cte as (select 1 as a) select a from cte")
    }

    @Test
    fun `createQuery assigns next ordering value`() {
        val graph = graph(id = 1L)
        val existingEntity = QueryEntity(
            id = 5L,
            graph = graph,
            name = "existing",
            sqlText = "select 1",
            ordering = 3,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        val savedEntity = QueryEntity(
            id = 10L,
            graph = graph,
            name = "new",
            sqlText = "select 2",
            ordering = 4,
            updatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6),
        )

        whenever(graphRepository.findById(1L)).thenReturn(Optional.of(graph))
        whenever(queryRepository.existsByGraphIdAndName(1L, "new")).thenReturn(false)
        whenever(queryRepository.findMaxOrderingByGraphId(1L)).thenReturn(3)
        whenever(queryRepository.save(any<QueryEntity>())).thenReturn(savedEntity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.QUERY)).thenReturn(null)

        queryService.createQuery(1L, CreateQueryRequest("new", "select 2"))

        val captor = argumentCaptor<QueryEntity>()
        verify(queryRepository).save(captor.capture())
        assertThat(captor.firstValue.ordering).isEqualTo(4)
    }

    @Test
    fun `updateOrdering throws when graph not found`() {
        whenever(graphRepository.existsById(99L)).thenReturn(false)

        assertThatThrownBy {
            queryService.updateOrdering(99L, listOf(QueryOrderingEntry(1L, 0)))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Graph 99 not found")
    }

    @Test
    fun `updateOrdering throws when query not found in graph`() {
        whenever(graphRepository.existsById(1L)).thenReturn(true)
        whenever(queryRepository.findAllByGraphIdAndIdIn(1L, listOf(99L))).thenReturn(emptyList())

        assertThatThrownBy {
            queryService.updateOrdering(1L, listOf(QueryOrderingEntry(99L, 0)))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Queries not found in graph 1: [99]")
    }

    @Test
    fun `updateOrdering updates ordering and returns sorted result`() {
        val graph = graph(id = 1L)
        val query1 = QueryEntity(
            id = 10L,
            graph = graph,
            name = "first",
            sqlText = "select 1",
            ordering = 0,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        val query2 = QueryEntity(
            id = 11L,
            graph = graph,
            name = "second",
            sqlText = "select 2",
            ordering = 1,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )

        whenever(graphRepository.existsById(1L)).thenReturn(true)
        whenever(queryRepository.findAllByGraphIdAndIdIn(1L, listOf(10L, 11L)))
            .thenReturn(listOf(query1, query2))
        whenever(queryRepository.saveAll(any<Collection<QueryEntity>>()))
            .thenReturn(listOf(query1, query2))
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.QUERY)).thenReturn(null)
        whenever(changeService.getChangeByEntityIdAndEntityType(11L, EntityType.QUERY)).thenReturn(null)

        val entries = listOf(
            QueryOrderingEntry(10L, 2),
            QueryOrderingEntry(11L, 1),
        )
        val result = queryService.updateOrdering(1L, entries)

        assertThat(query1.ordering).isEqualTo(2)
        assertThat(query2.ordering).isEqualTo(1)
        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo(11L)
        assertThat(result[1].id).isEqualTo(10L)
    }

    private fun graph(id: Long) = GraphEntity(
        id = id,
        category = category(id = 1L),
        name = "graph",
        graphType = GraphType.LINE,
        updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
    )

    private fun category(id: Long) = GraphCategoryEntity(
        id = id,
        dashboard = dashboard(id = 1L),
        name = "category",
        ordering = 0,
        updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
    )

    private fun dashboard(id: Long) = DashboardEntity(
        id = id,
        project = project(id = 1L),
        name = "dashboard",
        description = null,
        updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
    )

    private fun project(id: Long) = ProjectEntity(
        id = id,
        name = "project",
        description = null,
        updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
    )
}
