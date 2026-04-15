package no.nav.reops.sidegroup

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
    controllers = [SidegroupController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, OAuth2ResourceServerAutoConfiguration::class],
)
@Import(SidegroupControllerTest.MockConfig::class, ControlAdvice::class)
class SidegroupControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val sidegroupService: SidegroupService,
) {

    @TestConfiguration
    class MockConfig {
        @Bean
        fun sidegroupService(): SidegroupService = Mockito.mock(SidegroupService::class.java)
    }

    private val updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5)
    private val websiteId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Test
    fun `POST creates sidegroup and returns 201`() {
        val dto = sidegroupDto(id = 1L, name = "Product pages", include = listOf("/products/", "/shop/"), exclude = listOf("/products/archived/"))
        whenever(sidegroupService.createSidegroup(any())).thenReturn(dto)

        val result = mockMvc.post("/api/sidegroup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Product pages","websiteId":"11111111-1111-1111-1111-111111111111","include":["/products/","/shop/"],"exclude":["/products/archived/"]}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(201)
        val body = result.response.contentAsString
        assertThat(body).contains("\"id\":1")
        assertThat(body).contains("\"name\":\"Product pages\"")
        assertThat(body).contains("\"/products/\"")
        assertThat(body).contains("\"/shop/\"")
        assertThat(body).contains("\"/products/archived/\"")
    }

    @Test
    fun `POST with only name creates sidegroup with empty filters`() {
        val dto = sidegroupDto(id = 1L, name = "All pages")
        whenever(sidegroupService.createSidegroup(any())).thenReturn(dto)

        val result = mockMvc.post("/api/sidegroup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"All pages","websiteId":"11111111-1111-1111-1111-111111111111"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(201)
        val body = result.response.contentAsString
        assertThat(body).contains("\"name\":\"All pages\"")
        assertThat(body).contains("\"include\":[]")
    }

    @Test
    fun `POST with duplicate name returns 400`() {
        whenever(sidegroupService.createSidegroup(any())).thenThrow(
            IllegalArgumentException("Sidegroup with name 'Product pages' already exists")
        )

        val result = mockMvc.post("/api/sidegroup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Product pages","websiteId":"11111111-1111-1111-1111-111111111111","include":["/products/"]}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString).contains("already exists")
    }

    @Test
    fun `GET returns all sidegroups with 200`() {
        val sidegroups = listOf(
            sidegroupDto(id = 1L, name = "Blog pages", include = listOf("/blog/", "/articles/")),
            sidegroupDto(id = 2L, name = "Admin pages", startWith = listOf("/admin/")),
        )
        whenever(sidegroupService.getAllSidegroup()).thenReturn(sidegroups)

        val result = mockMvc.get("/api/sidegroup") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        val body = result.response.contentAsString
        assertThat(body).contains("\"name\":\"Blog pages\"")
        assertThat(body).contains("\"name\":\"Admin pages\"")
    }

    @Test
    fun `GET returns empty list with 200`() {
        whenever(sidegroupService.getAllSidegroup()).thenReturn(emptyList())

        val result = mockMvc.get("/api/sidegroup") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("[]")
    }

    @Test
    fun `GET with websiteId filters by website`() {
        val sidegroups = listOf(
            sidegroupDto(id = 1L, name = "Blog pages", include = listOf("/blog/")),
        )
        whenever(sidegroupService.getSidegroupsByWebsiteId(websiteId)).thenReturn(sidegroups)

        val result = mockMvc.get("/api/sidegroup?websiteId=$websiteId") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        val body = result.response.contentAsString
        assertThat(body).contains("\"name\":\"Blog pages\"")
    }

    @Test
    fun `GET by id returns sidegroup with 200`() {
        val dto = sidegroupDto(id = 1L, name = "FAQ pages", exact = listOf("/faq", "/help/faq"))
        whenever(sidegroupService.getSidegroupById(1L)).thenReturn(dto)

        val result = mockMvc.get("/api/sidegroup/1") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        val body = result.response.contentAsString
        assertThat(body).contains("\"id\":1")
        assertThat(body).contains("\"name\":\"FAQ pages\"")
        assertThat(body).contains("\"/faq\"")
        assertThat(body).contains("\"/help/faq\"")
    }

    @Test
    fun `GET by id returns 404 when not found`() {
        whenever(sidegroupService.getSidegroupById(999L)).thenReturn(null)

        val result = mockMvc.get("/api/sidegroup/999") {
            accept = MediaType.APPLICATION_JSON
        }.andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `PUT updates sidegroup and returns 200`() {
        val dto = sidegroupDto(id = 1L, name = "Landing pages", include = listOf("/landing/", "/promo/"), endWith = listOf("/signup"))
        whenever(sidegroupService.updateSidegroup(any(), any())).thenReturn(dto)

        val result = mockMvc.put("/api/sidegroup/1") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Landing pages","include":["/landing/","/promo/"],"endWith":["/signup"]}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        val body = result.response.contentAsString
        assertThat(body).contains("\"name\":\"Landing pages\"")
        assertThat(body).contains("\"/landing/\"")
        assertThat(body).contains("\"/promo/\"")
        assertThat(body).contains("\"/signup\"")
    }

    @Test
    fun `PUT returns 404 when not found`() {
        whenever(sidegroupService.updateSidegroup(any(), any())).thenReturn(null)

        val result = mockMvc.put("/api/sidegroup/999") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Landing pages","include":["/landing/"]}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `PUT with explicit empty arrays clears filters`() {
        val dto = sidegroupDto(id = 1L, name = "Blog pages")
        whenever(sidegroupService.updateSidegroup(any(), any())).thenReturn(dto)

        val result = mockMvc.put("/api/sidegroup/1") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Blog pages","include":[],"exclude":[]}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        val body = result.response.contentAsString
        assertThat(body).contains("\"include\":[]")
    }

    @Test
    fun `PUT with only name sends null filters to service`() {
        val dto = sidegroupDto(id = 1L, name = "Renamed group", include = listOf("/kept/"))
        whenever(sidegroupService.updateSidegroup(any(), any())).thenReturn(dto)

        val result = mockMvc.put("/api/sidegroup/1") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Renamed group"}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("\"name\":\"Renamed group\"")
    }

    @Test
    fun `DELETE returns 204 on success`() {
        whenever(sidegroupService.deleteSidegroup(1L)).thenReturn(true)

        val result = mockMvc.delete("/api/sidegroup/1").andReturn()

        assertThat(result.response.status).isEqualTo(204)
    }

    @Test
    fun `DELETE returns 404 when not found`() {
        whenever(sidegroupService.deleteSidegroup(999L)).thenReturn(false)

        val result = mockMvc.delete("/api/sidegroup/999").andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    private fun sidegroupDto(
        id: Long,
        name: String = "sidegroup",
        description: String? = null,
        websiteId: UUID = this.websiteId,
        include: List<String> = emptyList(),
        exclude: List<String> = emptyList(),
        exact: List<String> = emptyList(),
        startWith: List<String> = emptyList(),
        endWith: List<String> = emptyList(),
    ) = SidegroupDto(
        id = id,
        name = name,
        description = description,
        websiteId = websiteId,
        include = include,
        exclude = exclude,
        exact = exact,
        startWith = startWith,
        endWith = endWith,
        updatedAt = updatedAt,
        changedByEmail = null,
        changedByName = null,
        changedByNavIdent = null,
    )
}
