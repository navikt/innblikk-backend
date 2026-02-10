package no.nav.reops.change

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Change")
@RestController
@RequestMapping("/api/changes")
class ChangeController(private val changeService: ChangeService) {

    @GetMapping("/{entityType}/{entityId}")
    fun getChangesByEntityTypeAndEntityId(
        @PathVariable entityType: String,
        @PathVariable entityId: Long,
    ): ResponseEntity<ChangeDto> {
        val entityType = EntityType.valueOf(entityType.uppercase())
        val change = changeService.getChangeByEntityIdAndEntityType(entityId, entityType)
        return if (change != null) ResponseEntity.ok(change) else ResponseEntity.notFound().build()
    }

    @GetMapping("/{entityType}/{entityId}/all")
    fun getAllChangesByEntityTypeAndEntityId(
        @PathVariable entityType: String,
        @PathVariable entityId: Long,
    ): ResponseEntity<List<ChangeDto>> {
        val entityType = EntityType.valueOf(entityType.uppercase())
        val changes = changeService.getAllChangeByEntityIdAndEntityType(entityId, entityType)
        return ResponseEntity.ok(changes)
    }
}