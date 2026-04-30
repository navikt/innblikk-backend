package no.nav.reops.userstats

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "UserStats")
@RequestMapping("/api/stats")
class UserStatsController(private val userStatsService: UserStatsService) {

    @GetMapping
    fun getStats(): ResponseEntity<UserStatsResponse> =
        ResponseEntity.ok(userStatsService.getStats())
}
