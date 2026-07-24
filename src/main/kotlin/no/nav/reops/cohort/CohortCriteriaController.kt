package no.nav.reops.cohort

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "CohortCriteria")
@RequestMapping("/api/cohort/{cohortId}/criteria")
class CohortCriteriaController(private val cohortCriteriaService: CohortCriteriaService) {

    @PutMapping
    fun replaceCriteria(
        @PathVariable cohortId: Long,
        @RequestBody root: CohortNodeRequest,
    ): ResponseEntity<CohortNodeDto> {
        val result = cohortCriteriaService.replaceCriteria(cohortId, root)
        return ResponseEntity.ok(result)
    }
}
