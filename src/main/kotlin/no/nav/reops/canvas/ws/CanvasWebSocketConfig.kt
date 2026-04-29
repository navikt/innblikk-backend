package no.nav.reops.canvas.ws

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class CanvasWebSocketConfig(
    private val handler: CanvasWebSocketHandler,
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(handler, "/api/canvas/ws")
            .setAllowedOriginPatterns(
                "https://innblikk.ansatt.nav.no",
                "https://innblikk.ansatt.dev.nav.no",
                "https://startumami.ansatt.nav.no",
                "https://startumami.ansatt.dev.nav.no",
                "http://localhost:*",
            )
    }
}
