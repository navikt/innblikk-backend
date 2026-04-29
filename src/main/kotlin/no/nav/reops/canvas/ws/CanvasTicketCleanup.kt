package no.nav.reops.canvas.ws

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CanvasTicketCleanup(
    private val canvasDataService: CanvasDataService,
) {
    @Scheduled(fixedRate = 60_000)
    fun cleanupExpiredTickets() {
        canvasDataService.cleanupExpiredTickets()
    }
}
