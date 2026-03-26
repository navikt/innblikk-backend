package no.nav.reops.sidegroup

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Sidegroup")
@RequestMapping("/api/sidegroup")
class SidegroupController(private val sideGroupService: SidegroupService) {

    @PostMapping
    fun createSidegroup(
        @RequestBody request: CreateSidegroupRequest,
    ): ResponseEntity<SidegroupDto> {
        val sidegroup = sideGroupService.createSidegroup(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(sidegroup)
    }

    @GetMapping
    fun getAllSidegroup(): ResponseEntity<List<SidegroupDto>> {
        val sidegroup = sideGroupService.getAllSidegroup()
        return ResponseEntity.ok(sidegroup)
    }

    @GetMapping("/{id}")
    fun getSidegroupById(
        @PathVariable id: Long
    ): ResponseEntity<SidegroupDto> {
        val sidegroup = sideGroupService.getSidegroupById(id)
        return if (sidegroup != null) ResponseEntity.ok(sidegroup) else ResponseEntity.notFound().build()
    }

    @PutMapping("/{id}")
    fun updateSidegroup(
        @PathVariable id: Long,
        @RequestBody request: UpdateSidegroupRequest,
    ): ResponseEntity<SidegroupDto> {
        val updated = sideGroupService.updateSidegroup(id, request)
        return if (updated != null) ResponseEntity.ok(updated) else ResponseEntity.notFound().build()
    }

    @DeleteMapping("/{id}")
    fun deleteSidegroup(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        val deleted = sideGroupService.deleteSidegroup(id)
        return if (deleted) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }
}