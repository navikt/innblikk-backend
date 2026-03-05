package no.nav.reops.query

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
    controllers = [QueryController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, OAuth2ResourceServerAutoConfiguration::class],
)
@Import(QueryControllerTest.MockConfig::class, ControlAdvice::class)
class QueryControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val queryService: QueryService,
) {

    @TestConfiguration
    class MockConfig {
        @Bean
        fun queryService(): QueryService = Mockito.mock(QueryService::class.java)
    }

    private val basePath = "/api/projects/1/dashboards/2/categories/3/graphs/4/queries"
    private val updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5)

    @Test
    fun `POST creates query and returns 201`() {
        val dto = QueryDto(id = 10L, graphId = 4L, name = "query", sqlText = "select 1", ordering = 0, updatedAt = updatedAt)
        whenever(queryService.createQuery(eq(4L), any())).thenReturn(dto)

        val result = mockMvc.post(basePath) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"query","sqlText":"select 1"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(201)
        val body = result.response.contentAsString
        assertThat(body).contains("\"id\":10")
        assertThat(body).contains("\"graphId\":4")
        assertThat(body).contains("\"name\":\"query\"")
        assertThat(body).contains("\"sqlText\":\"select 1\"")
    }

    @Test
    fun `POST returns 400 when graph not found`() {
        whenever(queryService.createQuery(eq(4L), any()))
            .thenThrow(IllegalArgumentException("Graph 4 not found"))

        val result = mockMvc.post(basePath) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"query","sqlText":"select 1"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString).contains("Graph 4 not found")
    }

    @Test
    fun `POST returns 400 when duplicate name`() {
        whenever(queryService.createQuery(eq(4L), any()))
            .thenThrow(IllegalArgumentException("Graph with name 'query' already exists in graph 4"))

        val result = mockMvc.post(basePath) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"query","sqlText":"select 1"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString).contains("already exists")
    }

    @Test
    fun `POST returns 400 when sql is invalid`() {
        whenever(queryService.createQuery(eq(4L), any()))
            .thenThrow(IllegalArgumentException("Only SELECT queries are allowed"))

        val result = mockMvc.post(basePath) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"query","sqlText":"drop table users"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString).contains("Only SELECT queries are allowed")
    }

    @Test
    fun `GET returns all queries with 200`() {
        val queries = listOf(
            QueryDto(id = 10L, graphId = 4L, name = "first", sqlText = "select 1", ordering = 0, updatedAt = updatedAt),
            QueryDto(id = 11L, graphId = 4L, name = "second", sqlText = "select 2", ordering = 1, updatedAt = updatedAt),
        )
        whenever(queryService.getAllQuery(4L)).thenReturn(queries)

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
        whenever(queryService.getAllQuery(4L)).thenReturn(emptyList())

        val result = mockMvc.get(basePath) {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("[]")
    }

    @Test
    fun `GET by id returns query with 200`() {
        val dto = QueryDto(id = 10L, graphId = 4L, name = "query", sqlText = "select 1", ordering = 0, updatedAt = updatedAt)
        whenever(queryService.getQueryById(4L, 10L)).thenReturn(dto)

        val result = mockMvc.get("$basePath/10") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"id\":10")
    }

    @Test
    fun `GET by id returns 404 when not found`() {
        whenever(queryService.getQueryById(4L, 999L)).thenReturn(null)

        val result = mockMvc.get("$basePath/999") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `PUT updates query and returns 200`() {
        val dto = QueryDto(id = 10L, graphId = 4L, name = "updated", sqlText = "select 2", ordering = 0, updatedAt = updatedAt)
        whenever(queryService.updateQuery(eq(4L), eq(10L), any())).thenReturn(dto)

        val result = mockMvc.put("$basePath/10") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"updated","sqlText":"select 2"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"name\":\"updated\"")
        assertThat(result.response.contentAsString).contains("\"sqlText\":\"select 2\"")
    }

    @Test
    fun `PUT returns 404 when not found`() {
        whenever(queryService.updateQuery(eq(4L), eq(999L), any())).thenReturn(null)

        val result = mockMvc.put("$basePath/999") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"updated","sqlText":"select 1"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `PUT returns 400 when sql is invalid`() {
        whenever(queryService.updateQuery(eq(4L), eq(10L), any()))
            .thenThrow(IllegalArgumentException("Only SELECT queries are allowed"))

        val result = mockMvc.put("$basePath/10") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"updated","sqlText":"delete from users"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString).contains("Only SELECT queries are allowed")
    }

    @Test
    fun `DELETE returns 204 on success`() {
        whenever(queryService.deleteQuery(4L, 10L)).thenReturn(true)

        val result = mockMvc.delete("$basePath/10").andReturn()

        assertThat(result.response.status).isEqualTo(204)
    }

    @Test
    fun `DELETE returns 404 when not found`() {
        whenever(queryService.deleteQuery(4L, 999L)).thenReturn(false)

        val result = mockMvc.delete("$basePath/999").andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `POST ordering updates ordering and returns 200`() {
        val result1 = QueryDto(id = 10L, graphId = 4L, name = "a", sqlText = "select 1", ordering = 1, updatedAt = updatedAt)
        val result2 = QueryDto(id = 11L, graphId = 4L, name = "b", sqlText = "select 2", ordering = 0, updatedAt = updatedAt)
        whenever(queryService.updateOrdering(eq(4L), any())).thenReturn(listOf(result2, result1))

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
    fun `POST ordering returns 400 when graph not found`() {
        whenever(queryService.updateOrdering(eq(4L), any()))
            .thenThrow(IllegalArgumentException("Graph 4 not found"))

        val result = mockMvc.post("$basePath/ordering") {
            contentType = MediaType.APPLICATION_JSON
            content = """[{"id":10,"ordering":0}]"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString).contains("Graph 4 not found")
    }
}

