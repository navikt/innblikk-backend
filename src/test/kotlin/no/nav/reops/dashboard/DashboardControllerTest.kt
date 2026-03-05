package no.nav.reops.dashboard

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
    controllers = [DashboardController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, OAuth2ResourceServerAutoConfiguration::class],
)
@Import(DashboardControllerTest.MockConfig::class, ControlAdvice::class)
class DashboardControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val dashboardService: DashboardService,
) {

    @TestConfiguration
    class MockConfig {
        @Bean
        fun dashboardService(): DashboardService = Mockito.mock(DashboardService::class.java)
    }

    private val basePath = "/api/projects/1/dashboards"
    private val updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5)

    @Test
    fun `POST creates dashboard and returns 201`() {
        val dto = DashboardDto(id = 10L, projectId = 1L, name = "dash", description = "desc", updatedAt = updatedAt)
        whenever(dashboardService.createDashboard(eq(1L), any())).thenReturn(dto)

        val result = mockMvc.post(basePath) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"dash","description":"desc"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(201)
        val body = result.response.contentAsString
        assertThat(body).contains("\"id\":10")
        assertThat(body).contains("\"projectId\":1")
        assertThat(body).contains("\"name\":\"dash\"")
    }

    @Test
    fun `POST returns 400 when project not found`() {
        whenever(dashboardService.createDashboard(eq(1L), any()))
            .thenThrow(IllegalArgumentException("Project 1 not found"))

        val result = mockMvc.post(basePath) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"dash","description":null}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString).contains("Project 1 not found")
    }

    @Test
    fun `GET returns all dashboards with 200`() {
        val dashboards = listOf(
            DashboardDto(id = 10L, projectId = 1L, name = "first", description = null, updatedAt = updatedAt),
            DashboardDto(id = 11L, projectId = 1L, name = "second", description = "desc", updatedAt = updatedAt),
        )
        whenever(dashboardService.getAllDashboards(1L)).thenReturn(dashboards)

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
        whenever(dashboardService.getAllDashboards(1L)).thenReturn(emptyList())

        val result = mockMvc.get(basePath) {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("[]")
    }

    @Test
    fun `GET by id returns dashboard with 200`() {
        val dto = DashboardDto(id = 10L, projectId = 1L, name = "dash", description = null, updatedAt = updatedAt)
        whenever(dashboardService.getDashboardById(1L, 10L)).thenReturn(dto)

        val result = mockMvc.get("$basePath/10") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"id\":10")
    }

    @Test
    fun `GET by id returns 404 when not found`() {
        whenever(dashboardService.getDashboardById(1L, 999L)).thenReturn(null)

        val result = mockMvc.get("$basePath/999") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `PUT updates dashboard and returns 200`() {
        val dto = DashboardDto(id = 10L, projectId = 1L, name = "updated", description = "new", updatedAt = updatedAt)
        whenever(dashboardService.updateDashboard(eq(1L), eq(10L), any())).thenReturn(dto)

        val result = mockMvc.put("$basePath/10") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"updated","description":"new"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"name\":\"updated\"")
    }

    @Test
    fun `PUT returns 404 when not found`() {
        whenever(dashboardService.updateDashboard(eq(1L), eq(999L), any())).thenReturn(null)

        val result = mockMvc.put("$basePath/999") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"updated","description":null}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `PUT returns 400 when duplicate name`() {
        whenever(dashboardService.updateDashboard(eq(1L), eq(10L), any()))
            .thenThrow(IllegalArgumentException("Dashboard with name 'dup' already exists in project 1"))

        val result = mockMvc.put("$basePath/10") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"dup","description":null}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString).contains("already exists")
    }

    @Test
    fun `DELETE returns 204 on success`() {
        whenever(dashboardService.deleteDashboard(1L, 10L)).thenReturn(true)

        val result = mockMvc.delete("$basePath/10").andReturn()

        assertThat(result.response.status).isEqualTo(204)
    }

    @Test
    fun `DELETE returns 404 when not found`() {
        whenever(dashboardService.deleteDashboard(1L, 999L)).thenReturn(false)

        val result = mockMvc.delete("$basePath/999").andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }
}

