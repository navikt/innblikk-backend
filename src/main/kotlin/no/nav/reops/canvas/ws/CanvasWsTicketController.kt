package no.nav.reops.canvas.ws

import no.nav.reops.security.AuthenticatedUserProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/canvas")
class CanvasWsTicketController(
    private val canvasDataService: CanvasDataService,
    private val authenticatedUserProvider: AuthenticatedUserProvider,
) {
    @PostMapping("/ws-ticket")
    fun createWsTicket(): ResponseEntity<Map<String, String>> {
        val user = authenticatedUserProvider.getCurrentUser()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.navIdent ?: user.email ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val userName = user.name ?: userId

        val ticket = canvasDataService.createWsTicket(userId, userName)
        return ResponseEntity.ok(mapOf("ticket" to ticket))
    }
}
