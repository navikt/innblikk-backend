package no.nav.reops.usersettings

import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.reops.security.AuthenticatedUserProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "UserSettings")
@RequestMapping("/api/user-settings")
class UserSettingsController(
    private val userSettingsService: UserSettingsService,
    private val authenticatedUserProvider: AuthenticatedUserProvider,
) {
    @GetMapping
    fun getUserSettings(): ResponseEntity<GetUserSettingsResponse> {
        val navIdent = authenticatedUserProvider.getCurrentUser()?.navIdent
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.ok(userSettingsService.getSettings(navIdent))
    }

    @PutMapping
    fun upsertUserSettings(
        @RequestBody request: UpsertUserSettingsRequest,
    ): ResponseEntity<UpsertUserSettingsResponse> {
        val navIdent = authenticatedUserProvider.getCurrentUser()?.navIdent
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val result = userSettingsService.upsertSettings(navIdent, request)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/touch")
    fun touchUserSettings(): ResponseEntity<Void> {
        val navIdent = authenticatedUserProvider.getCurrentUser()?.navIdent
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        userSettingsService.touchUser(navIdent)
        return ResponseEntity.noContent().build()
    }
}
