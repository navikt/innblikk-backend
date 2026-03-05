package no.nav.reops.graph

import no.nav.reops.ControlAdvice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.mockito.Mockito
import java.time.LocalDateTime

@WebMvcTest(
    controllers = [GraphController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, OAuth2ResourceServerAutoConfiguration::class],
)
@Import(GraphControllerTest.MockConfig::class, ControlAdvice::class)
class GraphControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val graphService: GraphService,
) {

    @TestConfiguration
    class MockConfig {
        @Bean
        fun graphService(): GraphService = Mockito.mock(GraphService::class.java)
    }

    private val basePath = "/api/projects/1/dashboards/2/categories/3/graphs"
    private val updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5)

    @Test
    fun `POST creates graph and returns 201`() {
        val dto = GraphDto(
            id = 10L, categoryId = 3L, name = "graph", graphType = GraphType.LINE,
            width = 50, ordering = 0, updatedAt = updatedAt,
        )
        whenever(graphService.createGraph(eq(3L), any())).thenReturn(dto)

        val result = mockMvc.post(basePath) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"graph","graphType":"LINE"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(201)
        val body = result.response.contentAsString
        assertThat(body).contains("\"id\":10")
        assertThat(body).contains("\"categoryId\":3")
        assertThat(body).contains("\"name\":\"graph\"")
        assertThat(body).contains("\"graphType\":\"LINE\"")
    }

    @Test
    fun `POST returns 400 when category not found`() {
        whenever(graphService.createGraph(eq(3L), any()))
            .thenThrow(IllegalArgumentException("Category 3 not found"))

        val result = mockMvc.post(basePath) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"graph","graphType":null}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString).contains("Category 3 not found")
    }

    @Test
    fun `POST returns 400 when duplicate name`() {
        whenever(graphService.createGraph(eq(3L), any()))
            .thenThrow(IllegalArgumentException("Graph with name 'graph' already exists in category 3"))

        val result = mockMvc.post(basePath) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"graph","graphType":null}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString).contains("already exists")
    }

    @Test
    fun `GET returns all graphs with 200`() {
        val graphs = listOf(
            GraphDto(id = 10L, categoryId = 3L, name = "first", graphType = GraphType.BAR, width = 50, ordering = 0, updatedAt = updatedAt),
            GraphDto(id = 11L, categoryId = 3L, name = "second", graphType = null, width = 75, ordering = 1, updatedAt = updatedAt),
        )
        whenever(graphService.getAllGraphs(3L)).thenReturn(graphs)

        val result = mockMvc.get(basePath) {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        val body = result.response.contentAsString
        assertThat(body).contains("\"name\":\"first\"")
        assertThat(body).contains("\"name\":\"second\"")
    }

    @Test
    fun `GET returns empty list with 200`() {
        whenever(graphService.getAllGraphs(3L)).thenReturn(emptyList())

        val result = mockMvc.get(basePath) {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("[]")
    }

    @Test
    fun `GET by id returns graph with 200`() {
        val dto = GraphDto(
            id = 10L, categoryId = 3L, name = "graph", graphType = GraphType.PIE,
            width = 50, ordering = 0, updatedAt = updatedAt,
        )
        whenever(graphService.getGraphById(3L, 10L)).thenReturn(dto)

        val result = mockMvc.get("$basePath/10") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"id\":10")
    }

    @Test
    fun `GET by id returns 404 when not found`() {
        whenever(graphService.getGraphById(3L, 999L)).thenReturn(null)

        val result = mockMvc.get("$basePath/999") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `PUT updates graph and returns 200`() {
        val dto = GraphDto(
            id = 10L, categoryId = 3L, name = "updated", graphType = GraphType.TABLE,
            width = 100, ordering = 0, updatedAt = updatedAt,
        )
        whenever(graphService.updateGraph(eq(3L), eq(10L), any())).thenReturn(dto)

        val result = mockMvc.put("$basePath/10") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"updated","graphType":"TABLE","width":100}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"name\":\"updated\"")
        assertThat(result.response.contentAsString).contains("\"graphType\":\"TABLE\"")
    }

    @Test
    fun `PUT returns 404 when not found`() {
        whenever(graphService.updateGraph(eq(3L), eq(999L), any())).thenReturn(null)

        val result = mockMvc.put("$basePath/999") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"updated","graphType":null}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `DELETE returns 204 on success`() {
        whenever(graphService.deleteGraph(3L, 10L)).thenReturn(true)

        val result = mockMvc.delete("$basePath/10").andReturn()

        assertThat(result.response.status).isEqualTo(204)
    }

    @Test
    fun `DELETE returns 404 when not found`() {
        whenever(graphService.deleteGraph(3L, 999L)).thenReturn(false)

        val result = mockMvc.delete("$basePath/999").andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `POST ordering updates ordering and returns 200`() {
        val result1 = GraphDto(id = 10L, categoryId = 3L, name = "a", graphType = null, width = 50, ordering = 1, updatedAt = updatedAt)
        val result2 = GraphDto(id = 11L, categoryId = 3L, name = "b", graphType = null, width = 50, ordering = 0, updatedAt = updatedAt)
        whenever(graphService.updateOrdering(eq(3L), any())).thenReturn(listOf(result2, result1))

        val result = mockMvc.post("$basePath/ordering") {
            contentType = MediaType.APPLICATION_JSON
            content = """[{"id":10,"ordering":1},{"id":11,"ordering":0}]"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        val body = result.response.contentAsString
        assertThat(body).contains("\"id\":10")
        assertThat(body).contains("\"id\":11")
    }

    @Test
    fun `POST ordering returns 400 when category not found`() {
        whenever(graphService.updateOrdering(eq(3L), any()))
            .thenThrow(IllegalArgumentException("Category 3 not found"))

        val result = mockMvc.post("$basePath/ordering") {
            contentType = MediaType.APPLICATION_JSON
            content = """[{"id":10,"ordering":0}]"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString).contains("Category 3 not found")
    }
}

