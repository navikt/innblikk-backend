package no.nav.reops.cohort

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "CohortEntry")
@RequestMapping("/api/cohort/{cohortId}/entries")
class CohortEntryController(private val cohortEntryService: CohortEntryService) {

    @PostMapping
    fun createEntry(
        @PathVariable cohortId: Long,
        @RequestBody request: CreateCohortEntryRequest,
    ): ResponseEntity<CohortEntryDto> {
        val entry = cohortEntryService.createEntry(cohortId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(entry)
    }

    @PutMapping("/{id}")
    fun updateEntry(
        @PathVariable cohortId: Long,
        @PathVariable id: Long,
        @RequestBody request: CreateCohortEntryRequest,
    ): ResponseEntity<CohortEntryDto> {
        val updated = cohortEntryService.updateEntry(cohortId, id, request)
        return if (updated != null) ResponseEntity.ok(updated) else ResponseEntity.notFound().build()
    }

    @DeleteMapping("/{id}")
    fun deleteEntry(
        @PathVariable cohortId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        val deleted = cohortEntryService.deleteEntry(cohortId, id)
        return if (deleted) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    @PostMapping("/ordering")
    fun updateOrdering(
        @PathVariable cohortId: Long,
        @RequestBody entries: List<CohortEntryOrderingEntry>,
    ): ResponseEntity<List<CohortEntryDto>> {
        val result = cohortEntryService.updateOrdering(cohortId, entries)
        return ResponseEntity.ok(result)
    }
}
