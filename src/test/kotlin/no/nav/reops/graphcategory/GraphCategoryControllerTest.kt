package no.nav.reops.graphcategory

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
    controllers = [GraphCategoryController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, OAuth2ResourceServerAutoConfiguration::class],
)
@Import(GraphCategoryControllerTest.MockConfig::class, ControlAdvice::class)
class GraphCategoryControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val graphCategoryService: GraphCategoryService,
) {

    @TestConfiguration
    class MockConfig {
        @Bean
        fun graphCategoryService(): GraphCategoryService = Mockito.mock(GraphCategoryService::class.java)
    }

    private val basePath = "/api/projects/1/dashboards/2/categories"
    private val updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5)

    @Test
    fun `POST creates category and returns 201`() {
        val dto = GraphCategoryDto(id = 10L, dashboardId = 2L, name = "cat", ordering = 0, updatedAt = updatedAt)
        whenever(graphCategoryService.createCategory(eq(2L), any())).thenReturn(dto)

        val result = mockMvc.post(basePath) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"cat"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(201)
        val body = result.response.contentAsString
        assertThat(body).contains("\"id\":10")
        assertThat(body).contains("\"dashboardId\":2")
        assertThat(body).contains("\"name\":\"cat\"")
    }

    @Test
    fun `POST returns 400 when dashboard not found`() {
        whenever(graphCategoryService.createCategory(eq(2L), any()))
            .thenThrow(IllegalArgumentException("Dashboard 2 not found"))

        val result = mockMvc.post(basePath) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"cat"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString).contains("Dashboard 2 not found")
    }

    @Test
    fun `POST returns 400 when duplicate name`() {
        whenever(graphCategoryService.createCategory(eq(2L), any()))
            .thenThrow(IllegalArgumentException("Category with name 'cat' already exists in dashboard 2"))

        val result = mockMvc.post(basePath) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"cat"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString).contains("already exists")
    }

    @Test
    fun `GET returns all categories with 200`() {
        val categories = listOf(
            GraphCategoryDto(id = 10L, dashboardId = 2L, name = "first", ordering = 0, updatedAt = updatedAt),
            GraphCategoryDto(id = 11L, dashboardId = 2L, name = "second", ordering = 1, updatedAt = updatedAt),
        )
        whenever(graphCategoryService.getAllCategories(2L)).thenReturn(categories)

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
        whenever(graphCategoryService.getAllCategories(2L)).thenReturn(emptyList())

        val result = mockMvc.get(basePath) {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("[]")
    }

    @Test
    fun `GET by id returns category with 200`() {
        val dto = GraphCategoryDto(id = 10L, dashboardId = 2L, name = "cat", ordering = 0, updatedAt = updatedAt)
        whenever(graphCategoryService.getCategoryById(2L, 10L)).thenReturn(dto)

        val result = mockMvc.get("$basePath/10") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"id\":10")
    }

    @Test
    fun `GET by id returns 404 when not found`() {
        whenever(graphCategoryService.getCategoryById(2L, 999L)).thenReturn(null)

        val result = mockMvc.get("$basePath/999") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `PUT updates category and returns 200`() {
        val dto = GraphCategoryDto(id = 10L, dashboardId = 2L, name = "updated", ordering = 0, updatedAt = updatedAt)
        whenever(graphCategoryService.updateCategory(eq(2L), eq(10L), any())).thenReturn(dto)

        val result = mockMvc.put("$basePath/10") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"updated"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"name\":\"updated\"")
    }

    @Test
    fun `PUT returns 404 when not found`() {
        whenever(graphCategoryService.updateCategory(eq(2L), eq(999L), any())).thenReturn(null)

        val result = mockMvc.put("$basePath/999") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"updated"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `DELETE returns 204 on success`() {
        whenever(graphCategoryService.deleteCategory(2L, 10L)).thenReturn(true)

        val result = mockMvc.delete("$basePath/10").andReturn()

        assertThat(result.response.status).isEqualTo(204)
    }

    @Test
    fun `DELETE returns 404 when not found`() {
        whenever(graphCategoryService.deleteCategory(2L, 999L)).thenReturn(false)

        val result = mockMvc.delete("$basePath/999").andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `POST ordering updates ordering and returns 200`() {
        val result1 = GraphCategoryDto(id = 10L, dashboardId = 2L, name = "a", ordering = 1, updatedAt = updatedAt)
        val result2 = GraphCategoryDto(id = 11L, dashboardId = 2L, name = "b", ordering = 0, updatedAt = updatedAt)
        whenever(graphCategoryService.updateOrdering(eq(2L), any())).thenReturn(listOf(result2, result1))

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
    fun `POST ordering returns 400 when dashboard not found`() {
        whenever(graphCategoryService.updateOrdering(eq(2L), any()))
            .thenThrow(IllegalArgumentException("Dashboard 2 not found"))

        val result = mockMvc.post("$basePath/ordering") {
            contentType = MediaType.APPLICATION_JSON
            content = """[{"id":10,"ordering":0}]"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString).contains("Dashboard 2 not found")
    }
}

