package pl.edu.webrtc.infrastructure.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import io.quarkus.websockets.next.WebSocketConnection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.edu.webrtc.application.service.RoomSignalingService
import pl.edu.webrtc.domain.model.*

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

    @BeforeEach
    fun setUp() {
        signalingService = mockk()
        sessionRegistry = mockk(relaxed = true)
        objectMapper = ObjectMapper()
        webSocket = SignalingWebSocket(signalingService, sessionRegistry, objectMapper)

        connection = mockk(relaxed = true)
        every { connection.id() } returns "conn-test"
        every { connection.isOpen } returns true
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
    fun `onMessage should send ERROR frame when exception occurs`() {
        val slotJson = slot<String>()
        justRun { connection.sendTextAndAwait(capture(slotJson)) }

        webSocket.onMessage("invalid", "{}", connection)

        val response = objectMapper.readValue(slotJson.captured, Map::class.java)
        assertEquals("ERROR", response["type"])
    }

    @Test
    fun `onClose should unregister connection from registry`() {
        webSocket.onClose(connection)
        verify(exactly = 1) { sessionRegistry.unregisterByConnectionId("conn-test") }
    }
}