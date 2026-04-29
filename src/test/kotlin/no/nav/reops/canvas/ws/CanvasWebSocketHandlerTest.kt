package no.nav.reops.canvas.ws

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class CanvasWebSocketHandlerTest {

    @Mock
    private lateinit var jwtDecoder: JwtDecoder

    @Mock
    private lateinit var pgNotifyBridge: CanvasPgNotifyBridge

    @Mock
    private lateinit var canvasDataService: CanvasDataService

    @Mock
    private lateinit var session: WebSocketSession

    private val objectMapper: ObjectMapper = ObjectMapper()
    private lateinit var handler: CanvasWebSocketHandler

    @BeforeEach
    fun setUp() {
        handler = CanvasWebSocketHandler(jwtDecoder, pgNotifyBridge, canvasDataService)
        handler.init()
        whenever(session.id).thenReturn("test-session-1")
        whenever(session.isOpen).thenReturn(true)
        handler.afterConnectionEstablished(session)
    }

    private fun sendMessage(msg: Map<String, Any?>) {
        handler.handleMessage(session, TextMessage(objectMapper.writeValueAsString(msg)))
    }

    private fun captureLastSentMessage(): Map<String, Any?> {
        val captor = org.mockito.ArgumentCaptor.forClass(TextMessage::class.java)
        org.mockito.Mockito.verify(session, atLeast(1)).sendMessage(captor.capture())
        return objectMapper.readValue(captor.allValues.last().payload, object : TypeReference<Map<String, Any?>>() {})
    }

    private fun captureAllSentMessages(): List<Map<String, Any?>> {
        val captor = org.mockito.ArgumentCaptor.forClass(TextMessage::class.java)
        org.mockito.Mockito.verify(session, atLeast(1)).sendMessage(captor.capture())
        return captor.allValues.map {
            objectMapper.readValue(it.payload, object : TypeReference<Map<String, Any?>>() {})
        }
    }

    private fun authenticateSession() {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("NAVident", "T123456")
            .claim("name", "Test User")
            .claim("preferred_username", "test@nav.no")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
        whenever(jwtDecoder.decode("valid-token")).thenReturn(jwt)

        sendMessage(mapOf("type" to "auth", "token" to "valid-token"))
    }

    // ── Auth ────────────────────────────────────────────────────────────────

    @Nested
    inner class Auth {

        @Test
        fun `successful auth sends auth ok`() {
            authenticateSession()

            val response = captureLastSentMessage()
            assertThat(response["type"]).isEqualTo("auth:ok")
        }

        @Test
        fun `auth with invalid token sends error`() {
            whenever(jwtDecoder.decode("bad-token")).thenThrow(RuntimeException("Invalid"))

            sendMessage(mapOf("type" to "auth", "token" to "bad-token"))

            val response = captureLastSentMessage()
            assertThat(response["type"]).isEqualTo("error")
            assertThat(response["message"] as String).contains("Authentication failed")
        }

        @Test
        fun `auth with missing token sends error`() {
            sendMessage(mapOf("type" to "auth"))

            val response = captureLastSentMessage()
            assertThat(response["type"]).isEqualTo("error")
            assertThat(response["message"] as String).contains("token")
        }
    }

    // ── Unauthenticated requests ────────────────────────────────────────────

    @Nested
    inner class Unauthenticated {

        @Test
        fun `non-auth messages rejected when not authenticated`() {
            sendMessage(mapOf("type" to "join", "projectId" to 1, "dashboardId" to 1))

            val response = captureLastSentMessage()
            assertThat(response["type"]).isEqualTo("error")
            assertThat(response["message"] as String).contains("Not authenticated")
        }

        @Test
        fun `ping always works without auth`() {
            sendMessage(mapOf("type" to "ping"))

            val response = captureLastSentMessage()
            assertThat(response["type"]).isEqualTo("pong")
        }
    }

    // ── Join ────────────────────────────────────────────────────────────────

    @Nested
    inner class Join {

        @Test
        fun `join sends lock state and canvas state`() {
            authenticateSession()

            val canvasState = mapOf<String, Any?>("categories" to emptyList<Any>(), "entries" to emptyList<Any>())
            whenever(canvasDataService.getCanvasState(1L, 2L)).thenReturn(canvasState)
            whenever(canvasDataService.getTimerState(2L)).thenReturn(null)
            whenever(canvasDataService.getDotVotingState(2L)).thenReturn(mapOf("session" to null, "ballots" to emptyList<Any>()))
            whenever(canvasDataService.getLocksForDashboard(2L)).thenReturn(emptyMap())

            sendMessage(mapOf("type" to "join", "projectId" to 1, "dashboardId" to 2))

            val messages = captureAllSentMessages()
            // Should include: auth:ok, canvas:lock:state, canvas:state, canvas:timer, canvas:dotvoting
            val events = messages.filter { it["type"] == "event" }.map { it["event"] }
            assertThat(events).contains("canvas:lock:state", "canvas:state", "canvas:timer", "canvas:dotvoting")
        }

        @Test
        fun `join with missing params sends error`() {
            authenticateSession()

            sendMessage(mapOf("type" to "join", "projectId" to 1))

            val messages = captureAllSentMessages()
            val errorMsg = messages.last()
            assertThat(errorMsg["type"]).isEqualTo("error")
            assertThat(errorMsg["message"] as String).contains("dashboardId")
        }
    }

    // ── Locks ───────────────────────────────────────────────────────────────

    @Nested
    inner class Locks {

        @BeforeEach
        fun joinRoom() {
            authenticateSession()
            whenever(canvasDataService.getCanvasState(1L, 1L)).thenReturn(emptyMap())
            whenever(canvasDataService.getTimerState(1L)).thenReturn(null)
            whenever(canvasDataService.getDotVotingState(1L)).thenReturn(mapOf("session" to null, "ballots" to emptyList<Any>()))
            whenever(canvasDataService.getLocksForDashboard(1L)).thenReturn(emptyMap())
            sendMessage(mapOf("type" to "join", "projectId" to 1, "dashboardId" to 1))
        }

        @Test
        fun `lock acquire sends acquired event`() {
            whenever(canvasDataService.acquireLock(any(), any(), any(), any(), any()))
                .thenReturn(CanvasDataService.LockResult(true, CanvasDataService.LockEntry("user1", "User 1", Instant.now().plusSeconds(15))))

            sendMessage(mapOf("type" to "lock:acquire", "frameGraphId" to 42, "ownerId" to "user1", "ownerLabel" to "User 1"))

            val messages = captureAllSentMessages()
            val acquired = messages.find { (it["event"] as? String) == "canvas:lock:acquired" }
            assertThat(acquired).isNotNull
            @Suppress("UNCHECKED_CAST")
            val payload = acquired!!["payload"] as Map<String, Any?>
            assertThat(payload["frameGraphId"]).isEqualTo(42)
        }

        @Test
        fun `lock acquire denied when held by another`() {
            whenever(canvasDataService.acquireLock(any(), any(), any(), any(), any()))
                .thenReturn(CanvasDataService.LockResult(false, CanvasDataService.LockEntry("user1", "User 1", Instant.now().plusSeconds(15))))

            sendMessage(mapOf("type" to "lock:acquire", "frameGraphId" to 42, "ownerId" to "user2", "ownerLabel" to "User 2"))

            val messages = captureAllSentMessages()
            val denied = messages.find { (it["event"] as? String) == "canvas:lock:denied" }
            assertThat(denied).isNotNull
        }

        @Test
        fun `lock release broadcasts updated state`() {
            whenever(canvasDataService.releaseLock(any(), any(), any())).thenReturn(true)
            whenever(canvasDataService.getLocksForDashboard(1L)).thenReturn(emptyMap())

            sendMessage(mapOf("type" to "lock:release", "frameGraphId" to 42, "ownerId" to "user1"))

            verify(canvasDataService).releaseLock(org.mockito.kotlin.eq(1L), org.mockito.kotlin.eq(42), org.mockito.kotlin.eq("user1"))
            val messages = captureAllSentMessages()
            val stateEvent = messages.find { (it["event"] as? String) == "canvas:lock:state" }
            assertThat(stateEvent).isNotNull
        }
    }

    // ── Save ────────────────────────────────────────────────────────────────

    @Nested
    inner class Save {

        @BeforeEach
        fun joinRoom() {
            authenticateSession()
            whenever(canvasDataService.getCanvasState(1L, 1L)).thenReturn(emptyMap())
            whenever(canvasDataService.getTimerState(1L)).thenReturn(null)
            whenever(canvasDataService.getDotVotingState(1L)).thenReturn(mapOf("session" to null, "ballots" to emptyList<Any>()))
            whenever(canvasDataService.getLocksForDashboard(1L)).thenReturn(emptyMap())
            sendMessage(mapOf("type" to "join", "projectId" to 1, "dashboardId" to 1))
        }

        @Test
        fun `save calls data service and sends saved event`() {
            val savedResult = mapOf<String, Any?>("queryId" to 100L, "version" to 1L)
            whenever(canvasDataService.saveFrame(any(), any())).thenReturn(savedResult)

            sendMessage(mapOf(
                "type" to "save",
                "graphId" to 10, "queryId" to 100,
                "sqlText" to "SELECT 'x' AS canvas_config",
                "version" to 0,
                "requestId" to "req-123",
            ))

            val messages = captureAllSentMessages()
            val savedMsg = messages.find { (it["event"] as? String) == "canvas:saved" }
            assertThat(savedMsg).isNotNull
            @Suppress("UNCHECKED_CAST")
            val payload = savedMsg!!["payload"] as Map<String, Any?>
            assertThat(payload["requestId"]).isEqualTo("req-123")
            assertThat(payload["queryId"]).isEqualTo(100)
        }

        @Test
        fun `save sends conflict on version mismatch`() {
            whenever(canvasDataService.saveFrame(any(), any())).thenThrow(
                CanvasVersionConflictException("conflict", mapOf("version" to 5L))
            )

            sendMessage(mapOf(
                "type" to "save",
                "graphId" to 10, "queryId" to 100,
                "sqlText" to "SELECT 'x' AS canvas_config",
                "version" to 3,
                "requestId" to "req-456",
            ))

            val messages = captureAllSentMessages()
            val conflict = messages.find { (it["event"] as? String) == "canvas:save:conflict" }
            assertThat(conflict).isNotNull
            @Suppress("UNCHECKED_CAST")
            val payload = conflict!!["payload"] as Map<String, Any?>
            assertThat(payload["requestId"]).isEqualTo("req-456")
        }

        @Test
        fun `save without joining room sends error`() {
            // Use a fresh session that hasn't joined
            val freshSession = org.mockito.Mockito.mock(WebSocketSession::class.java)
            whenever(freshSession.id).thenReturn("fresh-session")
            whenever(freshSession.isOpen).thenReturn(true)

            // Authenticate the fresh session
            val jwt = Jwt.withTokenValue("t2")
                .header("alg", "none")
                .claim("NAVident", "X999")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build()
            whenever(jwtDecoder.decode("t2")).thenReturn(jwt)
            handler.afterConnectionEstablished(freshSession)
            handler.handleMessage(freshSession, TextMessage(objectMapper.writeValueAsString(mapOf("type" to "auth", "token" to "t2"))))

            handler.handleMessage(freshSession, TextMessage(objectMapper.writeValueAsString(mapOf(
                "type" to "save", "graphId" to 10, "sqlText" to "x",
            ))))

            val captor = org.mockito.ArgumentCaptor.forClass(TextMessage::class.java)
            org.mockito.Mockito.verify(freshSession, atLeast(1)).sendMessage(captor.capture())
            val lastMsg = objectMapper.readValue(captor.allValues.last().payload, object : TypeReference<Map<String, Any?>>() {})
            assertThat(lastMsg["type"]).isEqualTo("error")
            assertThat(lastMsg["message"] as String).contains("Must join a room")
        }
    }

    // ── Delete ──────────────────────────────────────────────────────────────

    @Nested
    inner class Delete {

        @BeforeEach
        fun joinRoom() {
            authenticateSession()
            whenever(canvasDataService.getCanvasState(1L, 1L)).thenReturn(emptyMap())
            whenever(canvasDataService.getTimerState(1L)).thenReturn(null)
            whenever(canvasDataService.getDotVotingState(1L)).thenReturn(mapOf("session" to null, "ballots" to emptyList<Any>()))
            whenever(canvasDataService.getLocksForDashboard(1L)).thenReturn(emptyMap())
            sendMessage(mapOf("type" to "join", "projectId" to 1, "dashboardId" to 1))
        }

        @Test
        fun `delete calls data service and sends deleted event`() {
            sendMessage(mapOf("type" to "delete", "graphId" to 10, "categoryId" to 1))

            verify(canvasDataService).deleteFrame(org.mockito.kotlin.eq(1L), org.mockito.kotlin.eq(10L), any())

            val messages = captureAllSentMessages()
            val deletedMsg = messages.find { (it["event"] as? String) == "canvas:frame:deleted" }
            assertThat(deletedMsg).isNotNull
        }

        @Test
        fun `delete with missing params sends error`() {
            sendMessage(mapOf("type" to "delete", "graphId" to 10))

            val messages = captureAllSentMessages()
            val errorMsg = messages.last()
            assertThat(errorMsg["type"]).isEqualTo("error")
            assertThat(errorMsg["message"] as String).contains("categoryId")
        }
    }

    // ── Timer ───────────────────────────────────────────────────────────────

    @Nested
    inner class TimerMessages {

        @BeforeEach
        fun joinRoom() {
            authenticateSession()
            whenever(canvasDataService.getCanvasState(1L, 1L)).thenReturn(emptyMap())
            whenever(canvasDataService.getTimerState(1L)).thenReturn(null)
            whenever(canvasDataService.getDotVotingState(1L)).thenReturn(mapOf("session" to null, "ballots" to emptyList<Any>()))
            whenever(canvasDataService.getLocksForDashboard(1L)).thenReturn(emptyMap())
            sendMessage(mapOf("type" to "join", "projectId" to 1, "dashboardId" to 1))
        }

        @Test
        fun `timer save calls data service and sends saved event`() {
            val timerResult = mapOf<String, Any?>("id" to 200L, "version" to 1L)
            whenever(canvasDataService.saveTimerState(any(), any(), any())).thenReturn(timerResult)
            whenever(canvasDataService.getTimerState(1L)).thenReturn(timerResult)

            sendMessage(mapOf("type" to "timer:save", "sqlText" to "SELECT 'timer' AS canvas_timer"))

            val messages = captureAllSentMessages()
            val saved = messages.find { (it["event"] as? String) == "canvas:timer:saved" }
            assertThat(saved).isNotNull
        }

        @Test
        fun `timer clear calls data service and sends null timer event`() {
            sendMessage(mapOf("type" to "timer:clear"))

            verify(canvasDataService).clearTimer(any(), any())

            val messages = captureAllSentMessages()
            val timerEvt = messages.filter { (it["event"] as? String) == "canvas:timer" }
            // The last canvas:timer event after clear should have null payload
            val lastTimer = timerEvt.last()
            assertThat(lastTimer["payload"]).isNull()
        }
    }

    // ── Dot Voting ──────────────────────────────────────────────────────────

    @Nested
    inner class DotVotingMessages {

        @BeforeEach
        fun joinRoom() {
            authenticateSession()
            whenever(canvasDataService.getCanvasState(1L, 1L)).thenReturn(emptyMap())
            whenever(canvasDataService.getTimerState(1L)).thenReturn(null)
            whenever(canvasDataService.getDotVotingState(1L)).thenReturn(mapOf("session" to null, "ballots" to emptyList<Any>()))
            whenever(canvasDataService.getLocksForDashboard(1L)).thenReturn(emptyMap())
            sendMessage(mapOf("type" to "join", "projectId" to 1, "dashboardId" to 1))
        }

        @Test
        fun `dotvoting save session calls data service`() {
            val dvState = mapOf<String, Any?>("session" to mapOf("id" to 1L), "ballots" to emptyList<Any>())
            whenever(canvasDataService.saveDotVotingSession(any(), any(), any())).thenReturn(emptyMap())
            whenever(canvasDataService.getDotVotingState(1L)).thenReturn(dvState)

            sendMessage(mapOf("type" to "dotvoting:save:session", "sqlText" to "SELECT 'session' AS x"))

            verify(canvasDataService).saveDotVotingSession(any(), any(), any())

            val messages = captureAllSentMessages()
            val dvEvents = messages.filter { (it["event"] as? String) == "canvas:dotvoting" }
            // At least 2: one from join, one from save
            assertThat(dvEvents.size).isGreaterThanOrEqualTo(2)
        }

        @Test
        fun `dotvoting save ballot requires ownerId`() {
            sendMessage(mapOf("type" to "dotvoting:save:ballot", "sqlText" to "x"))

            val messages = captureAllSentMessages()
            val errorMsg = messages.last()
            assertThat(errorMsg["type"]).isEqualTo("error")
            assertThat(errorMsg["message"] as String).contains("ownerId")
        }

        @Test
        fun `dotvoting save ballot calls data service with ownerId`() {
            val dvState = mapOf<String, Any?>("session" to null, "ballots" to listOf(mapOf("id" to 1)))
            whenever(canvasDataService.saveDotVotingBallot(any(), any(), any(), any())).thenReturn(emptyMap())
            whenever(canvasDataService.getDotVotingState(1L)).thenReturn(dvState)

            sendMessage(mapOf("type" to "dotvoting:save:ballot", "ownerId" to "user123", "sqlText" to "SELECT 'ballot'"))

            verify(canvasDataService).saveDotVotingBallot(any(), org.mockito.kotlin.eq("user123"), any(), any())
        }

        @Test
        fun `dotvoting clear calls data service and sends empty state`() {
            sendMessage(mapOf("type" to "dotvoting:clear"))

            verify(canvasDataService).clearDotVoting(any(), any())

            val messages = captureAllSentMessages()
            val dvEvents = messages.filter { (it["event"] as? String) == "canvas:dotvoting" }
            val lastDv = dvEvents.last()
            @Suppress("UNCHECKED_CAST")
            val payload = lastDv["payload"] as Map<String, Any?>
            assertThat(payload["session"]).isNull()
            assertThat(payload["ballots"] as List<*>).isEmpty()
        }
    }

    // ── Broadcast ───────────────────────────────────────────────────────────

    @Nested
    inner class Broadcast {

        @Test
        fun `broadcast publishes to PG notify`() {
            authenticateSession()
            whenever(canvasDataService.getCanvasState(1L, 1L)).thenReturn(emptyMap())
            whenever(canvasDataService.getTimerState(1L)).thenReturn(null)
            whenever(canvasDataService.getDotVotingState(1L)).thenReturn(mapOf("session" to null, "ballots" to emptyList<Any>()))
            whenever(canvasDataService.getLocksForDashboard(1L)).thenReturn(emptyMap())
            sendMessage(mapOf("type" to "join", "projectId" to 1, "dashboardId" to 1))

            sendMessage(mapOf(
                "type" to "broadcast",
                "projectId" to 1, "dashboardId" to 1,
                "event" to "custom:event",
                "payload" to mapOf("data" to "value"),
            ))

            verify(pgNotifyBridge).notify(any())
        }
    }

    // ── Invalid messages ────────────────────────────────────────────────────

    @Nested
    inner class InvalidMessages {

        @Test
        fun `invalid JSON sends error`() {
            handler.handleMessage(session, TextMessage("not json"))

            val response = captureLastSentMessage()
            assertThat(response["type"]).isEqualTo("error")
            assertThat(response["message"] as String).contains("Invalid JSON")
        }

        @Test
        fun `missing type sends error`() {
            sendMessage(mapOf("data" to "value"))

            val response = captureLastSentMessage()
            assertThat(response["type"]).isEqualTo("error")
            assertThat(response["message"] as String).contains("type")
        }

        @Test
        fun `unknown type sends error`() {
            authenticateSession()

            sendMessage(mapOf("type" to "unknown:command"))

            val messages = captureAllSentMessages()
            val errorMsg = messages.last()
            assertThat(errorMsg["type"]).isEqualTo("error")
            assertThat(errorMsg["message"] as String).contains("Unknown message type")
        }
    }

    // ── Connection lifecycle ────────────────────────────────────────────────

    @Nested
    inner class ConnectionLifecycle {

        @Test
        fun `afterConnectionClosed cleans up session state`() {
            authenticateSession()
            whenever(canvasDataService.getCanvasState(1L, 1L)).thenReturn(emptyMap())
            whenever(canvasDataService.getTimerState(1L)).thenReturn(null)
            whenever(canvasDataService.getDotVotingState(1L)).thenReturn(mapOf("session" to null, "ballots" to emptyList<Any>()))
            whenever(canvasDataService.getLocksForDashboard(1L)).thenReturn(emptyMap())
            sendMessage(mapOf("type" to "join", "projectId" to 1, "dashboardId" to 1))

            handler.afterConnectionClosed(session, org.springframework.web.socket.CloseStatus.NORMAL)

            // After close, sending a message should require re-auth
            // Reset mock to clear previous invocations
            org.mockito.Mockito.reset(session)
            whenever(session.id).thenReturn("test-session-1")
            whenever(session.isOpen).thenReturn(true)

            sendMessage(mapOf("type" to "join", "projectId" to 1, "dashboardId" to 1))

            val response = captureLastSentMessage()
            assertThat(response["type"]).isEqualTo("error")
            assertThat(response["message"] as String).contains("Not authenticated")
        }
    }
}
