package no.nav.reops.project

import no.nav.reops.ControlAdvice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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
    controllers = [ProjectController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, OAuth2ResourceServerAutoConfiguration::class],
)
@Import(ProjectControllerTest.MockConfig::class, ControlAdvice::class)
class ProjectControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val projectService: ProjectService,
) {

    @TestConfiguration
    class MockConfig {
        @Bean
        fun projectService(): ProjectService = Mockito.mock(ProjectService::class.java)
    }

    private val updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5)

    @Test
    fun `POST creates project and returns 201`() {
        val dto = ProjectDto(id = 1L, name = "project", description = "desc", updatedAt = updatedAt)
        whenever(projectService.createProject(any())).thenReturn(dto)

        val result = mockMvc.post("/api/projects") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"project","description":"desc"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(201)
        val body = result.response.contentAsString
        assertThat(body).contains("\"id\":1")
        assertThat(body).contains("\"name\":\"project\"")
        assertThat(body).contains("\"description\":\"desc\"")
    }

    @Test
    fun `GET returns all projects with 200`() {
        val projects = listOf(
            ProjectDto(id = 1L, name = "first", description = null, updatedAt = updatedAt),
            ProjectDto(id = 2L, name = "second", description = "desc", updatedAt = updatedAt),
        )
        whenever(projectService.getAllProjects()).thenReturn(projects)

        val result = mockMvc.get("/api/projects") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        val body = result.response.contentAsString
        assertThat(body).contains("\"name\":\"first\"")
        assertThat(body).contains("\"name\":\"second\"")
    }

    @Test
    fun `GET returns empty list with 200`() {
        whenever(projectService.getAllProjects()).thenReturn(emptyList())

        val result = mockMvc.get("/api/projects") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("[]")
    }

    @Test
    fun `GET by id returns project with 200`() {
        val dto = ProjectDto(id = 1L, name = "project", description = null, updatedAt = updatedAt)
        whenever(projectService.getProjectById(1L)).thenReturn(dto)

        val result = mockMvc.get("/api/projects/1") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"id\":1")
    }

    @Test
    fun `GET by id returns 404 when not found`() {
        whenever(projectService.getProjectById(999L)).thenReturn(null)

        val result = mockMvc.get("/api/projects/999") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `PUT updates project and returns 200`() {
        val dto = ProjectDto(id = 1L, name = "updated", description = "new desc", updatedAt = updatedAt)
        whenever(projectService.updateProject(any(), any())).thenReturn(dto)

        val result = mockMvc.put("/api/projects/1") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"updated","description":"new desc"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"name\":\"updated\"")
    }

    @Test
    fun `PUT returns 404 when project not found`() {
        whenever(projectService.updateProject(any(), any())).thenReturn(null)

        val result = mockMvc.put("/api/projects/999") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"updated","description":null}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `DELETE returns 204 on success`() {
        whenever(projectService.deleteProject(1L)).thenReturn(true)

        val result = mockMvc.delete("/api/projects/1").andReturn()

        assertThat(result.response.status).isEqualTo(204)
    }

    @Test
    fun `DELETE returns 404 when not found`() {
        whenever(projectService.deleteProject(999L)).thenReturn(false)

        val result = mockMvc.delete("/api/projects/999").andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }
}

