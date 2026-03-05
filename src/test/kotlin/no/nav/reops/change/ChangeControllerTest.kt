package no.nav.reops.change

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
import org.springframework.test.web.servlet.get
import org.mockito.Mockito
import java.time.LocalDateTime

@WebMvcTest(
    controllers = [ChangeController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, OAuth2ResourceServerAutoConfiguration::class],
)
@Import(ChangeControllerTest.MockConfig::class)
class ChangeControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val changeService: ChangeService,
) {

    @TestConfiguration
    class MockConfig {
        @Bean
        fun changeService(): ChangeService = Mockito.mock(ChangeService::class.java)
    }

    @Test
    fun `getChangesByEntityTypeAndEntityId returns 200 with change`() {
        val dto = ChangeDto(
            entityId = 10L,
            entityType = EntityType.PROJECT,
            changeType = ChangeType.CREATED,
            updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
        )
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.PROJECT)).thenReturn(dto)

        val result = mockMvc.get("/api/changes/project/10") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        val body = result.response.contentAsString
        assertThat(body).contains("\"entityId\":10")
        assertThat(body).contains("\"entityType\":\"PROJECT\"")
        assertThat(body).contains("\"changeType\":\"CREATED\"")
    }

    @Test
    fun `getChangesByEntityTypeAndEntityId returns 404 when not found`() {
        whenever(changeService.getChangeByEntityIdAndEntityType(999L, EntityType.DASHBOARD)).thenReturn(null)

        val result = mockMvc.get("/api/changes/dashboard/999") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `getChangesByEntityTypeAndEntityId handles case-insensitive entity type`() {
        val dto = ChangeDto(
            entityId = 5L,
            entityType = EntityType.GRAPH,
            changeType = ChangeType.UPDATED,
            updatedAt = LocalDateTime.of(2025, 3, 4, 5, 6, 7),
        )
        whenever(changeService.getChangeByEntityIdAndEntityType(5L, EntityType.GRAPH)).thenReturn(dto)

        val result = mockMvc.get("/api/changes/GRAPH/5") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"entityType\":\"GRAPH\"")
    }

    @Test
    fun `getAllChangesByEntityTypeAndEntityId returns 200 with list`() {
        val changes = listOf(
            ChangeDto(
                entityId = 10L,
                entityType = EntityType.QUERY,
                changeType = ChangeType.UPDATED,
                updatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6),
            ),
            ChangeDto(
                entityId = 10L,
                entityType = EntityType.QUERY,
                changeType = ChangeType.CREATED,
                updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5),
            ),
        )
        whenever(changeService.getAllChangeByEntityIdAndEntityType(10L, EntityType.QUERY)).thenReturn(changes)

        val result = mockMvc.get("/api/changes/query/10/all") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        val body = result.response.contentAsString
        assertThat(body).contains("UPDATED")
        assertThat(body).contains("CREATED")
    }

    @Test
    fun `getAllChangesByEntityTypeAndEntityId returns 200 with empty list`() {
        whenever(changeService.getAllChangeByEntityIdAndEntityType(999L, EntityType.GRAPH_CATEGORY)).thenReturn(emptyList())

        val result = mockMvc.get("/api/changes/graph_category/999/all") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("[]")
    }
}

