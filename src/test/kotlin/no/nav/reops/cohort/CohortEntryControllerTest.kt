package no.nav.reops.cohort

import no.nav.reops.ControlAdvice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
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
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.LocalDateTime

@WebMvcTest(
    controllers = [CohortEntryController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, OAuth2ResourceServerAutoConfiguration::class],
)
@Import(CohortEntryControllerTest.MockConfig::class, ControlAdvice::class)
class CohortEntryControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val cohortEntryService: CohortEntryService,
) {

    @TestConfiguration
    class MockConfig {
        @Bean
        fun cohortEntryService(): CohortEntryService = Mockito.mock(CohortEntryService::class.java)
    }

    private val updatedAt = LocalDateTime.of(2026, 4, 13, 10, 0, 0)

    @Test
    fun `POST creates entry and returns 201`() {
        val dto = entryDto(id = 10L, condition = EntryConditionType.NOT_PERFORMED)
        whenever(cohortEntryService.createEntry(eq(1L), any())).thenReturn(dto)

        val result = mockMvc.post("/api/cohort/1/entries") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "condition":"NOT_PERFORMED",
                    "timeScope":"DURING",
                    "timeRangeType":"BETWEEN",
                    "timeStart":"2026-04-13T10:00:00",
                    "timeEnd":"2026-04-14T10:00:00",
                    "conditions":[
                        {"conditionType":"CONTAINS","field":"platform","value":"https://example.no"}
                    ]
                }
            """.trimIndent()
        }.andReturn()

        assertThat(result.response.status).isEqualTo(201)
        assertThat(result.response.contentAsString).contains("\"condition\":\"NOT_PERFORMED\"")
    }

    @Test
    fun `POST with cohort not found returns 400`() {
        whenever(cohortEntryService.createEntry(eq(99L), any())).thenThrow(
            IllegalArgumentException("Cohort 99 not found")
        )

        val result = mockMvc.post("/api/cohort/99/entries") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"condition":"NOT_PERFORMED","conditions":[]}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
    }

    @Test
    fun `PUT updates entry and returns 200`() {
        val dto = entryDto(id = 10L, condition = EntryConditionType.PERFORMED)
        whenever(cohortEntryService.updateEntry(eq(1L), eq(10L), any())).thenReturn(dto)

        val result = mockMvc.put("/api/cohort/1/entries/10") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "condition":"PERFORMED",
                    "operator":"AND",
                    "timeScope":"DURING",
                    "timeRangeType":"LAST",
                    "timeRangeValue":"7",
                    "timeRangeUnit":"days",
                    "conditions":[
                        {"conditionType":"EQUALS","field":"count","value":"5"}
                    ]
                }
            """.trimIndent()
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"condition\":\"PERFORMED\"")
    }

    @Test
    fun `PUT returns 404 when not found`() {
        whenever(cohortEntryService.updateEntry(eq(1L), eq(999L), any())).thenReturn(null)

        val result = mockMvc.put("/api/cohort/1/entries/999") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"condition":"NOT_PERFORMED","conditions":[]}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `DELETE returns 204 on success`() {
        whenever(cohortEntryService.deleteEntry(1L, 10L)).thenReturn(true)

        val result = mockMvc.delete("/api/cohort/1/entries/10").andReturn()

        assertThat(result.response.status).isEqualTo(204)
    }

    @Test
    fun `DELETE returns 404 when not found`() {
        whenever(cohortEntryService.deleteEntry(1L, 999L)).thenReturn(false)

        val result = mockMvc.delete("/api/cohort/1/entries/999").andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `POST ordering reorders entries and returns 200`() {
        val entries = listOf(
            entryDto(id = 11L, condition = EntryConditionType.PERFORMED, ordering = 0),
            entryDto(id = 10L, condition = EntryConditionType.NOT_PERFORMED, ordering = 1),
        )
        whenever(cohortEntryService.updateOrdering(eq(1L), any())).thenReturn(entries)

        val result = mockMvc.post("/api/cohort/1/entries/ordering") {
            contentType = MediaType.APPLICATION_JSON
            content = """[{"id":11,"ordering":0},{"id":10,"ordering":1}]"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"id\":11")
    }

    @Test
    fun `POST ordering with cohort not found returns 400`() {
        whenever(cohortEntryService.updateOrdering(eq(99L), any())).thenThrow(
            IllegalArgumentException("Cohort 99 not found")
        )

        val result = mockMvc.post("/api/cohort/99/entries/ordering") {
            contentType = MediaType.APPLICATION_JSON
            content = """[{"id":1,"ordering":0}]"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
    }

    private fun entryDto(
        id: Long,
        cohortId: Long = 1L,
        condition: EntryConditionType = EntryConditionType.NOT_PERFORMED,
        ordering: Int = 0,
    ) = CohortEntryDto(
        id = id, cohortId = cohortId, condition = condition, ordering = ordering,
        operator = null, referencedCohortId = null,
        timeScope = null, timeRangeType = null, timeRangeValue = null, timeRangeUnit = null, timeQualifier = null,
        timeStart = null, timeEnd = null,
        updatedAt = updatedAt, conditions = emptyList(),
    )
}
