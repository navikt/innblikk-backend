package no.nav.reops.cohort

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@Tag(name = "Cohort")
@RequestMapping("/api/cohort")
class CohortController(private val cohortService: CohortService) {

    @PostMapping
    fun createCohort(
        @RequestBody request: CreateCohortRequest,
    ): ResponseEntity<CohortDto> {
        val cohort = cohortService.createCohort(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(cohort)
    }

    @GetMapping
    fun getAllCohorts(
        @RequestParam(required = false) websiteId: UUID?,
    ): ResponseEntity<List<CohortDto>> {
        val cohorts = if (websiteId != null) {
            cohortService.getCohortsByWebsiteId(websiteId)
        } else {
            cohortService.getAllCohorts()
        }
        return ResponseEntity.ok(cohorts)
    }

    @GetMapping("/{id}")
    fun getCohortById(
        @PathVariable id: Long,
    ): ResponseEntity<CohortDetailDto> {
        val cohort = cohortService.getCohortById(id)
        return if (cohort != null) ResponseEntity.ok(cohort) else ResponseEntity.notFound().build()
    }

    @PutMapping("/{id}")
    fun updateCohort(
        @PathVariable id: Long,
        @RequestBody request: UpdateCohortRequest,
    ): ResponseEntity<CohortDto> {
        val updated = cohortService.updateCohort(id, request)
        return if (updated != null) ResponseEntity.ok(updated) else ResponseEntity.notFound().build()
    }

    @DeleteMapping("/{id}")
    fun deleteCohort(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        val deleted = cohortService.deleteCohort(id)
        return if (deleted) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    @PostMapping("/{id}/restore")
    fun restoreCohort(
        @PathVariable id: Long,
    ): ResponseEntity<CohortDto> {
        val restored = cohortService.restoreCohort(id)
        return if (restored != null) ResponseEntity.ok(restored) else ResponseEntity.notFound().build()
    }
}
