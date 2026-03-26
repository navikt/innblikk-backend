package no.nav.reops

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class TestErrorController {
    @GetMapping("/test/illegal")
    fun illegal(): String = throw IllegalArgumentException("Nope")

    data class Payload(val name: String)

    @PostMapping("/test/echo")
    fun echo(@RequestBody payload: Payload): Payload = payload

    @GetMapping("/test/boom")
    fun boom(): String = throw RuntimeException("super-secret-details")

    @GetMapping("/test/duplicate")
    fun duplicate(): String = throw DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_sidegroups_name\"")
}

@WebMvcTest(
    controllers = [TestErrorController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, OAuth2ResourceServerAutoConfiguration::class],
)
@Import(ControlAdvice::class)
class ControllerAdviceTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `IllegalArgumentException becomes 400 with safe message and errorId`() {
        val result = mockMvc.get("/test/illegal") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentType).startsWith(MediaType.APPLICATION_JSON_VALUE)

        val body = result.response.contentAsString
        assertThat(body).contains("\"error\":\"BAD_REQUEST\"")
        assertThat(body).contains("\"message\":\"Nope\"")
        assertThat(body).contains("\"status\":400")
        assertThat(body).contains("\"errorId\":")
        // Should not leak stack traces
        assertThat(body).doesNotContain("IllegalArgumentException")
    }

    @Test
    fun `Malformed JSON becomes 400 without leaking parsing details`() {
        val result = mockMvc.post("/test/echo") {
            contentType = MediaType.APPLICATION_JSON
            accept = MediaType.APPLICATION_JSON
            content = "{not-json}"
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        val body = result.response.contentAsString
        assertThat(body).contains("\"error\":\"MALFORMED_REQUEST\"")
        assertThat(body).contains("Malformed request body")
        // Should not echo back Jackson internals / class names
        assertThat(body).doesNotContain("com.fasterxml")
    }

    @Test
    fun `Unhandled exception becomes 500 without leaking exception message`() {
        val result = mockMvc.get("/test/boom") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(500)
        val body = result.response.contentAsString
        assertThat(body).contains("\"error\":\"INTERNAL_ERROR\"")
        assertThat(body).contains("\"message\":\"Unexpected error\"")
        assertThat(body).contains("\"errorId\":")
        // don't leak the original exception message
        assertThat(body).doesNotContain("super-secret-details")
    }

    @Test
    fun `DataIntegrityViolationException becomes 409 Conflict`() {
        val result = mockMvc.get("/test/duplicate") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(409)
        val body = result.response.contentAsString
        assertThat(body).contains("\"error\":\"NOT_ALLOWED\"")
        assertThat(body).contains("\"errorId\":")
        assertThat(body).doesNotContain("uq_sidegroups_name")
    }

    @Test
    fun `Unsupported HTTP method becomes 405`() {
        val result = mockMvc.post("/test/illegal") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(405)
        val body = result.response.contentAsString
        assertThat(body).contains("\"error\":\"METHOD_NOT_ALLOWED\"")
        assertThat(body).contains("\"message\":\"Method not allowed\"")
        assertThat(body).contains("\"errorId\":")
    }
}
