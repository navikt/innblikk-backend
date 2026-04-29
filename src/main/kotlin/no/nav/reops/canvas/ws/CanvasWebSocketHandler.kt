package no.nav.reops.canvas.ws

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.reops.security.AuthenticatedUser
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import jakarta.annotation.PostConstruct
import java.util.concurrent.ConcurrentHashMap

private const val LOCK_TTL_MS = 15_000L
private const val WS_SEND_TIME_LIMIT_MS = 10_000
private const val WS_BUFFER_SIZE_LIMIT = 512 * 1024

data class AuthenticatedWsUser(
    val navIdent: String?,
    val name: String?,
    val email: String?,
)

@Component
class CanvasWebSocketHandler(
    private val jwtDecoder: JwtDecoder,
    private val pgNotifyBridge: CanvasPgNotifyBridge,
    private val canvasDataService: CanvasDataService,
) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()

    // Room key → set of sessions
    private val rooms = ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<WebSocketSession, Boolean>>()

    // Session ID → wrapped concurrent session
    private val wrappedSessions = ConcurrentHashMap<String, WebSocketSession>()

    // Session ID → room key
    private val sessionRoom = ConcurrentHashMap<String, String>()

    // Session ID → authenticated user
    private val sessionUser = ConcurrentHashMap<String, AuthenticatedWsUser>()

    @PostConstruct
    fun init() {
        pgNotifyBridge.setListener { payload ->
            handlePgNotification(payload)
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        wrappedSessions[session.id] = wrapSession(session)
        log.debug("WS connection opened: {}", session.id)
    }

    private fun wrapSession(session: WebSocketSession): WebSocketSession =
        ConcurrentWebSocketSessionDecorator(session, WS_SEND_TIME_LIMIT_MS, WS_BUFFER_SIZE_LIMIT)

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val wrapped = wrappedSessions.remove(session.id)
        leaveRoom(wrapped ?: session)
        sessionUser.remove(session.id)
        log.debug("WS connection closed: {} ({})", session.id, status)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        log.warn("WS transport error for {}: {}", session.id, exception.message)
        val wrapped = wrappedSessions.remove(session.id)
        leaveRoom(wrapped ?: session)
        sessionUser.remove(session.id)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val safe = wrappedSessions[session.id] ?: session
        val msg: Map<String, Any?>
        try {
            msg = objectMapper.readValue(message.payload, object : TypeReference<Map<String, Any?>>() {})
        } catch (_: Exception) {
            sendJson(safe, mapOf("type" to "error", "message" to "Invalid JSON"))
            return
        }

        val type = msg["type"] as? String
        if (type == null) {
            sendJson(safe, mapOf("type" to "error", "message" to "Missing 'type' field"))
            return
        }

        // Auth must be the first message
        if (type == "auth") {
            handleAuth(safe, msg)
            return
        }

        if (type == "ping") {
            sendJson(safe, mapOf("type" to "pong"))
            return
        }

        // All other message types require authentication
        if (!sessionUser.containsKey(safe.id)) {
            sendJson(safe, mapOf("type" to "error", "message" to "Not authenticated. Send auth message first."))
            return
        }

        when (type) {
            "join" -> handleJoin(safe, msg)
            "broadcast" -> handleBroadcast(safe, msg)
            "lock:acquire" -> handleLockAcquire(safe, msg)
            "lock:release" -> handleLockRelease(safe, msg)
            "lock:renew" -> handleLockRenew(safe, msg)
            "save" -> handleSave(safe, msg)
            "delete" -> handleDelete(safe, msg)
            "timer:save" -> handleTimerSave(safe, msg)
            "timer:clear" -> handleTimerClear(safe)
            "dotvoting:save:session" -> handleDotVotingSaveSession(safe, msg)
            "dotvoting:save:ballot" -> handleDotVotingSaveBallot(safe, msg)
            "dotvoting:clear" -> handleDotVotingClear(safe)
            else -> sendJson(safe, mapOf("type" to "error", "message" to "Unknown message type: $type"))
        }
    }

    // ── Auth ────────────────────────────────────────────────────────────────

    private fun handleAuth(session: WebSocketSession, msg: Map<String, Any?>) {
        val token = msg["token"] as? String
        if (token.isNullOrBlank()) {
            sendJson(session, mapOf("type" to "error", "message" to "Missing 'token' in auth message"))
            return
        }

        try {
            val jwt = jwtDecoder.decode(token)
            val user = AuthenticatedWsUser(
                navIdent = jwt.claims["NAVident"] as? String,
                name = jwt.claims["name"] as? String,
                email = jwt.claims["preferred_username"] as? String,
            )
            sessionUser[session.id] = user
            sendJson(session, mapOf("type" to "auth:ok"))
            log.info("WS auth OK for {} ({})", user.navIdent, session.id)
        } catch (e: Exception) {
            log.warn("WS auth failed for {}: {}", session.id, e.message)
            sendJson(session, mapOf("type" to "error", "message" to "Authentication failed"))
        }
    }

    // ── Join ────────────────────────────────────────────────────────────────

    private fun handleJoin(session: WebSocketSession, msg: Map<String, Any?>) {
        val projectId = (msg["projectId"] as? Number)?.toLong()
        val dashboardId = (msg["dashboardId"] as? Number)?.toLong()
        if (projectId == null || dashboardId == null) {
            sendJson(session, mapOf("type" to "error", "message" to "join requires numeric projectId and dashboardId"))
            return
        }

        leaveRoom(session)
        val roomKey = "$projectId:$dashboardId"
        val room = rooms.computeIfAbsent(roomKey) { ConcurrentHashMap.newKeySet() }
        room.add(session)
        sessionRoom[session.id] = roomKey

        val user = sessionUser[session.id]
        log.info("WS {} joined room {} ({} total on this pod)", user?.navIdent ?: "unknown", roomKey, room.size)

        // Send current lock state
        val lockState = serializeLocksForRoom(roomKey)
        sendJson(session, mapOf("type" to "event", "event" to "canvas:lock:state", "payload" to lockState))

        // Send full canvas state (Del 3)
        try {
            val canvasState = canvasDataService.getCanvasState(projectId, dashboardId)
            sendJson(session, mapOf("type" to "event", "event" to "canvas:state", "payload" to canvasState))
        } catch (e: Exception) {
            log.warn("Failed to load canvas state for room {}: {}", roomKey, e.message)
        }

        // Send timer state on join
        try {
            val timerState = canvasDataService.getTimerState(dashboardId)
            sendJson(session, mapOf("type" to "event", "event" to "canvas:timer", "payload" to timerState))
        } catch (e: Exception) {
            log.warn("Failed to load timer state for room {}: {}", roomKey, e.message)
        }

        // Send dot voting state on join
        try {
            val dotVotingState = canvasDataService.getDotVotingState(dashboardId)
            sendJson(session, mapOf("type" to "event", "event" to "canvas:dotvoting", "payload" to dotVotingState))
        } catch (e: Exception) {
            log.warn("Failed to load dot voting state for room {}: {}", roomKey, e.message)
        }
    }

    // ── Broadcast ───────────────────────────────────────────────────────────

    private fun handleBroadcast(session: WebSocketSession, msg: Map<String, Any?>) {
        val projectId = msg["projectId"]
        val dashboardId = msg["dashboardId"]
        val event = msg["event"] as? String
        val payload = msg["payload"]

        if (projectId == null || dashboardId == null || event == null) {
            sendJson(session, mapOf("type" to "error", "message" to "broadcast requires projectId, dashboardId, event"))
            return
        }

        val roomKey = "$projectId:$dashboardId"
        val outgoing = mapOf("type" to "event", "event" to event, "payload" to payload)

        broadcastAndPublish(roomKey, outgoing, excludeSession = session)
    }

    // ── Locks ───────────────────────────────────────────────────────────────

    private fun handleLockAcquire(session: WebSocketSession, msg: Map<String, Any?>) {
        val frameGraphId = (msg["frameGraphId"] as? Number)?.toInt()
        val ownerId = msg["ownerId"] as? String
        val ownerLabel = msg["ownerLabel"] as? String ?: "En kollega"

        if (frameGraphId == null || ownerId == null) {
            sendJson(session, mapOf("type" to "error", "message" to "lock:acquire requires frameGraphId and ownerId"))
            return
        }

        val roomKey = sessionRoom[session.id]
        if (roomKey == null) {
            sendJson(session, mapOf("type" to "error", "message" to "Must join a room before acquiring a lock"))
            return
        }

        val dashboardId = roomKey.split(":")[1].toLong()
        val result = canvasDataService.acquireLock(dashboardId, frameGraphId, ownerId, ownerLabel, LOCK_TTL_MS)

        if (!result.acquired && result.lock != null) {
            sendJson(session, mapOf(
                "type" to "event",
                "event" to "canvas:lock:denied",
                "payload" to mapOf(
                    "frameGraphId" to frameGraphId,
                    "ownerId" to result.lock.ownerId,
                    "ownerLabel" to result.lock.ownerLabel,
                    "expiresAt" to result.lock.expiresAt.toString(),
                ),
            ))
            return
        }

        val lockState = serializeLocksForRoom(roomKey)
        val stateEvent = mapOf("type" to "event", "event" to "canvas:lock:state", "payload" to lockState)

        sendJson(session, mapOf("type" to "event", "event" to "canvas:lock:acquired", "payload" to mapOf("frameGraphId" to frameGraphId)))
        broadcastAndPublish(roomKey, stateEvent, excludeSession = session)
    }

    private fun handleLockRelease(session: WebSocketSession, msg: Map<String, Any?>) {
        val frameGraphId = (msg["frameGraphId"] as? Number)?.toInt()
        val ownerId = msg["ownerId"] as? String

        if (frameGraphId == null || ownerId == null) {
            sendJson(session, mapOf("type" to "error", "message" to "lock:release requires frameGraphId and ownerId"))
            return
        }

        val roomKey = sessionRoom[session.id] ?: return
        val dashboardId = roomKey.split(":")[1].toLong()
        canvasDataService.releaseLock(dashboardId, frameGraphId, ownerId)

        val lockState = serializeLocksForRoom(roomKey)
        val stateEvent = mapOf("type" to "event", "event" to "canvas:lock:state", "payload" to lockState)
        broadcastAndPublish(roomKey, stateEvent, excludeSession = session)
        sendJson(session, stateEvent)
    }

    private fun handleLockRenew(session: WebSocketSession, msg: Map<String, Any?>) {
        val frameGraphId = (msg["frameGraphId"] as? Number)?.toInt()
        val ownerId = msg["ownerId"] as? String
        if (frameGraphId == null || ownerId == null) return

        val roomKey = sessionRoom[session.id] ?: return
        val dashboardId = roomKey.split(":")[1].toLong()
        canvasDataService.renewLock(dashboardId, frameGraphId, ownerId, LOCK_TTL_MS)
    }

    // ── Save (Del 3) ────────────────────────────────────────────────────────

    private fun handleSave(session: WebSocketSession, msg: Map<String, Any?>) {
        val roomKey = sessionRoom[session.id]
        if (roomKey == null) {
            sendJson(session, mapOf("type" to "error", "message" to "Must join a room before saving"))
            return
        }

        val requestId = msg["requestId"] as? String

        try {
            val result = canvasDataService.saveFrame(msg, getChangedBy(session))
            val savedPayload = if (requestId != null) result + ("requestId" to requestId) else result
            sendJson(session, mapOf("type" to "event", "event" to "canvas:saved", "payload" to savedPayload))

            val frameEvent = mapOf("type" to "event", "event" to "canvas:frame", "payload" to result)
            broadcastAndPublish(roomKey, frameEvent, excludeSession = session)
        } catch (e: CanvasVersionConflictException) {
            val conflictPayload = if (requestId != null) e.currentState + ("requestId" to requestId) else e.currentState
            sendJson(session, mapOf(
                "type" to "event",
                "event" to "canvas:save:conflict",
                "payload" to conflictPayload,
            ))
        } catch (e: Exception) {
            log.warn("Save failed for room {}: {}", roomKey, e.message)
            val errorPayload = mutableMapOf<String, Any?>("message" to "Save failed: ${e.message}")
            if (requestId != null) errorPayload["requestId"] = requestId
            sendJson(session, mapOf("type" to "error") + errorPayload)
        }
    }

    // ── Delete (Del 3) ──────────────────────────────────────────────────────

    private fun handleDelete(session: WebSocketSession, msg: Map<String, Any?>) {
        val roomKey = sessionRoom[session.id]
        if (roomKey == null) {
            sendJson(session, mapOf("type" to "error", "message" to "Must join a room before deleting"))
            return
        }

        try {
            val graphId = (msg["graphId"] as? Number)?.toLong()
            val categoryId = (msg["categoryId"] as? Number)?.toLong()
            if (graphId == null || categoryId == null) {
                sendJson(session, mapOf("type" to "error", "message" to "delete requires graphId and categoryId"))
                return
            }

            canvasDataService.deleteFrame(categoryId, graphId, getChangedBy(session))

            val deleteEvent = mapOf(
                "type" to "event",
                "event" to "canvas:frame:deleted",
                "payload" to mapOf("graphId" to graphId),
            )
            sendJson(session, deleteEvent)
            broadcastAndPublish(roomKey, deleteEvent, excludeSession = session)
        } catch (e: Exception) {
            log.warn("Delete failed for room {}: {}", roomKey, e.message)
            sendJson(session, mapOf("type" to "error", "message" to "Delete failed: ${e.message}"))
        }
    }

    // ── Timer ────────────────────────────────────────────────────────────────

    private fun handleTimerSave(session: WebSocketSession, msg: Map<String, Any?>) {
        val roomKey = sessionRoom[session.id]
        if (roomKey == null) {
            sendJson(session, mapOf("type" to "error", "message" to "Must join a room before saving timer"))
            return
        }

        try {
            val parts = roomKey.split(":")
            val dashboardId = parts[1].toLong()
            val result = canvasDataService.saveTimerState(dashboardId, msg, getChangedBy(session))

            // Respond to the sender
            sendJson(session, mapOf("type" to "event", "event" to "canvas:timer:saved", "payload" to result))

            // Broadcast the timer payload (sqlText) to all clients so they can update
            val timerState = canvasDataService.getTimerState(dashboardId)
            val timerEvent = mapOf("type" to "event", "event" to "canvas:timer", "payload" to timerState)
            broadcastAndPublish(roomKey, timerEvent, excludeSession = session)
        } catch (e: Exception) {
            log.warn("Timer save failed for room {}: {}", roomKey, e.message)
            sendJson(session, mapOf("type" to "error", "message" to "Timer save failed: ${e.message}"))
        }
    }

    private fun handleTimerClear(session: WebSocketSession) {
        val roomKey = sessionRoom[session.id]
        if (roomKey == null) {
            sendJson(session, mapOf("type" to "error", "message" to "Must join a room before clearing timer"))
            return
        }

        try {
            val parts = roomKey.split(":")
            val dashboardId = parts[1].toLong()
            canvasDataService.clearTimer(dashboardId, getChangedBy(session))

            val timerEvent = mapOf("type" to "event", "event" to "canvas:timer", "payload" to null)
            sendJson(session, timerEvent)
            broadcastAndPublish(roomKey, timerEvent, excludeSession = session)
        } catch (e: Exception) {
            log.warn("Timer clear failed for room {}: {}", roomKey, e.message)
            sendJson(session, mapOf("type" to "error", "message" to "Timer clear failed: ${e.message}"))
        }
    }

    // ── Dot Voting ──────────────────────────────────────────────────────────

    private fun handleDotVotingSaveSession(session: WebSocketSession, msg: Map<String, Any?>) {
        val roomKey = sessionRoom[session.id]
        if (roomKey == null) {
            sendJson(session, mapOf("type" to "error", "message" to "Must join a room before saving dot voting session"))
            return
        }

        try {
            val parts = roomKey.split(":")
            val dashboardId = parts[1].toLong()
            canvasDataService.saveDotVotingSession(dashboardId, msg, getChangedBy(session))

            val state = canvasDataService.getDotVotingState(dashboardId)
            val event = mapOf("type" to "event", "event" to "canvas:dotvoting", "payload" to state)
            sendJson(session, event)
            broadcastAndPublish(roomKey, event, excludeSession = session)
        } catch (e: Exception) {
            log.warn("Dot voting session save failed for room {}: {}", roomKey, e.message)
            sendJson(session, mapOf("type" to "error", "message" to "Dot voting session save failed: ${e.message}"))
        }
    }

    private fun handleDotVotingSaveBallot(session: WebSocketSession, msg: Map<String, Any?>) {
        val roomKey = sessionRoom[session.id]
        if (roomKey == null) {
            sendJson(session, mapOf("type" to "error", "message" to "Must join a room before saving ballot"))
            return
        }

        val ownerId = msg["ownerId"] as? String
        if (ownerId.isNullOrBlank()) {
            sendJson(session, mapOf("type" to "error", "message" to "dotvoting:save:ballot requires ownerId"))
            return
        }

        try {
            val parts = roomKey.split(":")
            val dashboardId = parts[1].toLong()
            canvasDataService.saveDotVotingBallot(dashboardId, ownerId, msg, getChangedBy(session))

            val state = canvasDataService.getDotVotingState(dashboardId)
            val event = mapOf("type" to "event", "event" to "canvas:dotvoting", "payload" to state)
            sendJson(session, event)
            broadcastAndPublish(roomKey, event, excludeSession = session)
        } catch (e: Exception) {
            log.warn("Dot voting ballot save failed for room {}: {}", roomKey, e.message)
            sendJson(session, mapOf("type" to "error", "message" to "Dot voting ballot save failed: ${e.message}"))
        }
    }

    private fun handleDotVotingClear(session: WebSocketSession) {
        val roomKey = sessionRoom[session.id]
        if (roomKey == null) {
            sendJson(session, mapOf("type" to "error", "message" to "Must join a room before clearing dot voting"))
            return
        }

        try {
            val parts = roomKey.split(":")
            val dashboardId = parts[1].toLong()
            canvasDataService.clearDotVoting(dashboardId, getChangedBy(session))

            val state = mapOf("session" to null, "ballots" to emptyList<Any>())
            val event = mapOf("type" to "event", "event" to "canvas:dotvoting", "payload" to state)
            sendJson(session, event)
            broadcastAndPublish(roomKey, event, excludeSession = session)
        } catch (e: Exception) {
            log.warn("Dot voting clear failed for room {}: {}", roomKey, e.message)
            sendJson(session, mapOf("type" to "error", "message" to "Dot voting clear failed: ${e.message}"))
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun leaveRoom(session: WebSocketSession) {
        val roomKey = sessionRoom.remove(session.id) ?: return
        val room = rooms[roomKey] ?: return
        room.remove(session)
        val remaining = room.size
        val user = sessionUser[session.id]
        log.info("WS {} left room {} ({} remaining on this pod)", user?.navIdent ?: "unknown", roomKey, remaining)
        if (remaining == 0) {
            rooms.remove(roomKey)
        }
    }

    private fun sendJson(session: WebSocketSession, data: Any) {
        if (!session.isOpen) return
        try {
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(data)))
        } catch (e: Exception) {
            log.debug("Failed to send WS message to {}: {}", session.id, e.message)
        }
    }

    private fun getChangedBy(session: WebSocketSession): AuthenticatedUser? {
        val wsUser = sessionUser[session.id] ?: return null
        return AuthenticatedUser(
            email = wsUser.email,
            name = wsUser.name,
            navIdent = wsUser.navIdent,
        )
    }

    private fun broadcastAndPublish(roomKey: String, data: Any, excludeSession: WebSocketSession? = null) {
        val raw = objectMapper.writeValueAsString(data)
        // Broadcast to local room clients
        val room = rooms[roomKey]
        if (room != null) {
            for (client in room) {
                if (client !== excludeSession && client.isOpen) {
                    try {
                        client.sendMessage(TextMessage(raw))
                    } catch (_: Exception) {
                    }
                }
            }
        }
        // Publish to other pods via PG NOTIFY (reuse serialized data)
        val envelope = """{"room":"${roomKey.replace("\"", "\\\"")}","data":$raw}"""
        pgNotifyBridge.notify(envelope)
    }

    private fun handlePgNotification(payload: String) {
        try {
            val envelope: Map<String, Any?> = objectMapper.readValue(payload, object : TypeReference<Map<String, Any?>>() {})
            val roomKey = envelope["room"] as? String ?: return
            val data = envelope["data"] ?: return

            // Forward to all local clients
            val raw = objectMapper.writeValueAsString(data)
            val room = rooms[roomKey] ?: return
            for (client in room) {
                if (client.isOpen) {
                    try {
                        client.sendMessage(TextMessage(raw))
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            log.debug("Error handling PG notification: {}", e.message)
        }
    }

    private fun serializeLocksForRoom(roomKey: String): Map<Int, Map<String, Any?>> {
        val dashboardId = roomKey.split(":")[1].toLong()
        return canvasDataService.getLocksForDashboard(dashboardId).mapValues { (_, entry) ->
            mapOf(
                "ownerId" to entry.ownerId,
                "ownerLabel" to entry.ownerLabel,
                "expiresAt" to entry.expiresAt.toString(),
            )
        }
    }
}
