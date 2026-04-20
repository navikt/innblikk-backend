package no.nav.reops.usersettings

import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.reops.security.AuthenticatedUserProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
    @PutMapping
    fun upsertUserSettings(
        @RequestBody request: UpsertUserSettingsRequest,
    ): ResponseEntity<UpsertUserSettingsResponse> {
        val navIdent = authenticatedUserProvider.getCurrentUser()?.navIdent
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val result = userSettingsService.upsertSettings(navIdent, request)
        return ResponseEntity.ok(result)
    }
}
