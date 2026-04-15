package no.nav.reops.cohort

import no.nav.reops.ControlAdvice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
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
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(
    controllers = [CohortController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, OAuth2ResourceServerAutoConfiguration::class],
)
@Import(CohortControllerTest.MockConfig::class, ControlAdvice::class)
class CohortControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val cohortService: CohortService,
) {

    @TestConfiguration
    class MockConfig {
        @Bean
        fun cohortService(): CohortService = Mockito.mock(CohortService::class.java)
    }

    private val updatedAt = LocalDateTime.of(2026, 4, 13, 10, 0, 0)
    private val websiteId = UUID.fromString("98c1936a-38a4-11f1-8197-5a715c0b0a36")

    @Test
    fun `POST creates cohort and returns 201`() {
        val dto = cohortDto(id = 1L, name = "Test cohort")
        whenever(cohortService.createCohort(any())).thenReturn(dto)

        val result = mockMvc.post("/api/cohort") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"websiteId":"$websiteId","name":"Test cohort","description":"desc"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(201)
        assertThat(result.response.contentAsString).contains("\"name\":\"Test cohort\"")
    }

    @Test
    fun `POST with duplicate name returns 400`() {
        whenever(cohortService.createCohort(any())).thenThrow(
            IllegalArgumentException("Cohort with name 'Existing' already exists")
        )

        val result = mockMvc.post("/api/cohort") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"websiteId":"$websiteId","name":"Existing"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString).contains("already exists")
    }

    @Test
    fun `GET returns all cohorts with 200`() {
        whenever(cohortService.getAllCohorts()).thenReturn(listOf(
            cohortDto(id = 1L, name = "A"), cohortDto(id = 2L, name = "B")
        ))

        val result = mockMvc.get("/api/cohort") { accept = MediaType.APPLICATION_JSON }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"name\":\"A\"")
        assertThat(result.response.contentAsString).contains("\"name\":\"B\"")
    }

    @Test
    fun `GET returns empty list with 200`() {
        whenever(cohortService.getAllCohorts()).thenReturn(emptyList())

        val result = mockMvc.get("/api/cohort") { accept = MediaType.APPLICATION_JSON }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("[]")
    }

    @Test
    fun `GET with websiteId filters by website`() {
        whenever(cohortService.getCohortsByWebsiteId(websiteId)).thenReturn(
            listOf(cohortDto(id = 1L, name = "Filtered"))
        )

        val result = mockMvc.get("/api/cohort?websiteId=$websiteId") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"name\":\"Filtered\"")
    }

    @Test
    fun `GET by id returns cohort detail with entries and 200`() {
        val detail = CohortDetailDto(
            id = 1L, websiteId = websiteId, name = "Detail", description = null,
            updatedAt = updatedAt, changedByEmail = null, changedByName = null, changedByNavIdent = null,
            entries = listOf(
                CohortEntryDto(
                    id = 10L, cohortId = 1L, condition = EntryCondition.NOT, ordering = 0,
                    operator = null, timeStart = updatedAt, timeEnd = updatedAt.plusDays(1),
                    timeRangeName = null, timeRangeUnit = null, updatedAt = updatedAt,
                    conditions = listOf(
                        CohortEntryConditionDto(
                            id = 100L, ordering = 1, conditionType = ConditionType.NOT_LIKE,
                            field = "platform", value = "https://example.no"
                        )
                    )
                )
            ),
        )
        whenever(cohortService.getCohortById(1L)).thenReturn(detail)

        val result = mockMvc.get("/api/cohort/1") { accept = MediaType.APPLICATION_JSON }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        val body = result.response.contentAsString
        assertThat(body).contains("\"name\":\"Detail\"")
        assertThat(body).contains("\"condition\":\"NOT\"")
        assertThat(body).contains("\"platform\"")
    }

    @Test
    fun `GET by id returns 404 when not found`() {
        whenever(cohortService.getCohortById(999L)).thenReturn(null)

        val result = mockMvc.get("/api/cohort/999") { accept = MediaType.APPLICATION_JSON }.andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `PUT updates cohort and returns 200`() {
        val dto = cohortDto(id = 1L, name = "Updated")
        whenever(cohortService.updateCohort(any(), any())).thenReturn(dto)

        val result = mockMvc.put("/api/cohort/1") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Updated","websiteId":"$websiteId"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"name\":\"Updated\"")
    }

    @Test
    fun `PUT returns 404 when not found`() {
        whenever(cohortService.updateCohort(any(), any())).thenReturn(null)

        val result = mockMvc.put("/api/cohort/999") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"X","websiteId":"$websiteId"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `DELETE returns 204 on success`() {
        whenever(cohortService.deleteCohort(1L)).thenReturn(true)

        val result = mockMvc.delete("/api/cohort/1").andReturn()

        assertThat(result.response.status).isEqualTo(204)
    }

    @Test
    fun `DELETE returns 404 when not found`() {
        whenever(cohortService.deleteCohort(999L)).thenReturn(false)

        val result = mockMvc.delete("/api/cohort/999").andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    private fun cohortDto(
        id: Long,
        name: String = "cohort",
        description: String? = null,
    ) = CohortDto(
        id = id, websiteId = websiteId, name = name, description = description,
        updatedAt = updatedAt, changedByEmail = null, changedByName = null, changedByNavIdent = null,
    )
}
