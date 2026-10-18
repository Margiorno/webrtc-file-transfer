package pl.edu.webrtc.infrastructure.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import io.quarkus.websockets.next.WebSocketConnection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.edu.webrtc.application.service.RoomSignalingService
import pl.edu.webrtc.domain.model.*
import java.util.UUID

class SignalingWebSocketTest {

    private lateinit var signalingService: RoomSignalingService
    private lateinit var sessionRegistry: WebSocketSessionRegistry
    private lateinit var objectMapper: ObjectMapper
    private lateinit var webSocket: SignalingWebSocket
    private lateinit var connection: WebSocketConnection

    private val roomCode = RoomCode("123456")
    private val hostId = ClientId.generate()
    private val hostSecret = ClientSecret.generate()
    private val guestId = ClientId.generate()

    private inline fun <reified T : Any, reified U : Any> MockKMatcherScope.anyInline(
        boxParamClass: Class<U>
    ): T {
        val boxMethod = T::class.java.getDeclaredMethod("box-impl", boxParamClass).apply {
            isAccessible = true
        }
        return boxMethod.invoke(null, any<U>()) as T
    }

    private fun MockKMatcherScope.anyClientId(): ClientId = anyInline(UUID::class.java)

    @BeforeEach
    fun setUp() {
        signalingService = mockk()
        sessionRegistry = mockk()
        objectMapper = ObjectMapper()
        webSocket = SignalingWebSocket(signalingService, sessionRegistry, objectMapper)

        connection = mockk(relaxed = true)
        every { connection.id() } returns "conn-test"
        every { connection.isOpen } returns true

        justRun { sessionRegistry.register(anyClientId(), any()) }
        every { sessionRegistry.sendToClient(anyClientId(), any()) } returns true
    }

    @Test
    fun `onMessage with AUTH should verify participant, register session and send AUTH_SUCCESS`() {
        val room = Room(roomCode, hostId, hostSecret, SecurityMode.STANDARD)
        every { signalingService.verifyParticipant(roomCode, hostId, hostSecret) } returns room

        val rawJson = objectMapper.writeValueAsString(
            IncomingWsMessage(WsMessageType.AUTH, hostId.value.toString(), hostSecret.value)
        )

        webSocket.onMessage("123456", rawJson, connection)

        verify(exactly = 1) { signalingService.verifyParticipant(roomCode, hostId, hostSecret) }
        verify(exactly = 1) { sessionRegistry.register(hostId, connection) }
        verify(exactly = 1) {
            sessionRegistry.sendToClient(
                hostId,
                OutgoingWsMessage(WsMessageType.AUTH_SUCCESS, "123456", RoomState.WAITING_FOR_GUEST)
            )
        }
    }

    @Test
    fun `onMessage with SDP_OFFER should resolve recipient and forward payload`() {
        val payload = mapOf("type" to "offer", "sdp" to "v=0...")
        every { signalingService.resolveSignalingRecipient(roomCode, hostId, hostSecret) } returns guestId

        val rawJson = objectMapper.writeValueAsString(
            IncomingWsMessage(WsMessageType.SDP_OFFER, hostId.value.toString(), hostSecret.value, payload)
        )

        webSocket.onMessage("123456", rawJson, connection)

        verify(exactly = 1) { signalingService.resolveSignalingRecipient(roomCode, hostId, hostSecret) }
        verify(exactly = 1) {
            sessionRegistry.sendToClient(
                guestId,
                OutgoingWsMessage(WsMessageType.SDP_OFFER, "123456", payload = payload)
            )
        }
    }

    @Test
    fun `onMessage with SDP_ANSWER and ICE_CANDIDATE should resolve recipient and forward payload`() {
        val answerPayload = mapOf("type" to "answer", "sdp" to "v=0...")
        val icePayload = mapOf("candidate" to "candidate:1 1 UDP...")
        every { signalingService.resolveSignalingRecipient(roomCode, hostId, hostSecret) } returns guestId

        val answerJson = objectMapper.writeValueAsString(
            IncomingWsMessage(WsMessageType.SDP_ANSWER, hostId.value.toString(), hostSecret.value, answerPayload)
        )
        val iceJson = objectMapper.writeValueAsString(
            IncomingWsMessage(WsMessageType.ICE_CANDIDATE, hostId.value.toString(), hostSecret.value, icePayload)
        )

        webSocket.onMessage("123456", answerJson, connection)
        webSocket.onMessage("123456", iceJson, connection)

        verify(exactly = 1) {
            sessionRegistry.sendToClient(
                guestId,
                OutgoingWsMessage(WsMessageType.SDP_ANSWER, "123456", payload = answerPayload)
            )
        }
        verify(exactly = 1) {
            sessionRegistry.sendToClient(
                guestId,
                OutgoingWsMessage(WsMessageType.ICE_CANDIDATE, "123456", payload = icePayload)
            )
        }
    }

    @Test
    fun `onMessage with unsupported incoming type should send ERROR frame`() {
        val slotJson = slot<String>()
        justRun { connection.sendTextAndAwait(capture(slotJson)) }

        val rawJson = objectMapper.writeValueAsString(
            IncomingWsMessage(WsMessageType.ROOM_ACTIVE, hostId.value.toString(), hostSecret.value)
        )

        webSocket.onMessage("123456", rawJson, connection)

        val response = objectMapper.readValue(slotJson.captured, Map::class.java)
        assertEquals("ERROR", response["type"])
        val payload = response["payload"] as Map<*, *>
        assertTrue(payload["message"].toString().contains("Unsupported incoming message type"))
    }

    @Test
    fun `onMessage should send ERROR frame when exception occurs and connection is open`() {
        val slotJson = slot<String>()
        justRun { connection.sendTextAndAwait(capture(slotJson)) }

        webSocket.onMessage("invalid", "{}", connection)

        val response = objectMapper.readValue(slotJson.captured, Map::class.java)
        assertEquals("ERROR", response["type"])
    }

    @Test
    fun `onMessage should not send ERROR frame when exception occurs but connection is already closed`() {
        every { connection.isOpen } returns false

        webSocket.onMessage("invalid", "{}", connection)

        verify(exactly = 0) { connection.sendTextAndAwait(any<String>()) }
    }

    @Test
    fun `onClose should unregister connection from registry`() {
        val realRegistry = spyk(WebSocketSessionRegistry(objectMapper))
        val wsWithRealRegistry = SignalingWebSocket(signalingService, realRegistry, objectMapper)

        realRegistry.register(hostId, connection)
        wsWithRealRegistry.onClose(connection)

        verify(exactly = 1) { realRegistry.unregisterByConnectionId("conn-test") }
    }
}