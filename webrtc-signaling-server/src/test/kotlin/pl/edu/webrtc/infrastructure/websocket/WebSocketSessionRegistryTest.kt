package pl.edu.webrtc.infrastructure.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import io.quarkus.websockets.next.WebSocketConnection
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.edu.webrtc.domain.model.ClientId
import pl.edu.webrtc.domain.model.RoomState

class WebSocketSessionRegistryTest {

    private lateinit var registry: WebSocketSessionRegistry
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        registry = WebSocketSessionRegistry(objectMapper)
    }

    @Test
    fun `register and sendToClient should serialize message and send when connection is open`() {
        val clientId = ClientId.generate()
        val connection = mockk<WebSocketConnection>()
        every { connection.id() } returns "conn-1"
        every { connection.isOpen } returns true
        justRun { connection.sendTextAndAwait(any<String>()) }

        registry.register(clientId, connection)

        val message = OutgoingWsMessage(
            type = WsMessageType.AUTH_SUCCESS,
            roomCode = "123456",
            state = RoomState.WAITING_FOR_GUEST
        )
        val sent = registry.sendToClient(clientId, message)

        assertTrue(sent)
        verify(exactly = 1) {
            connection.sendTextAndAwait(match<String> {
                it.contains("\"type\":\"AUTH_SUCCESS\"") && it.contains("\"roomCode\":\"123456\"")
            })
        }
    }

    @Test
    fun `sendToClient should return false when client is not registered`() {
        val sent = registry.sendToClient(
            ClientId.generate(),
            OutgoingWsMessage(WsMessageType.ROOM_ACTIVE, "123456")
        )
        assertFalse(sent)
    }

    @Test
    fun `sendToClient should unregister and return false when connection is closed`() {
        val clientId = ClientId.generate()
        val connection = mockk<WebSocketConnection>()
        every { connection.id() } returns "conn-closed"
        every { connection.isOpen } returns false

        registry.register(clientId, connection)

        val sent = registry.sendToClient(
            clientId,
            OutgoingWsMessage(WsMessageType.ROOM_ACTIVE, "123456")
        )

        assertFalse(sent)
        verify(exactly = 0) { connection.sendTextAndAwait(any<String>()) }
    }

    @Test
    fun `unregisterByConnectionId should remove session`() {
        val clientId = ClientId.generate()
        val connection = mockk<WebSocketConnection>()

        every { connection.id() } returns "conn-123"

        registry.register(clientId, connection)

        registry.unregisterByConnectionId("conn-123")

        val sentAfterRemove = registry.sendToClient(
            clientId,
            OutgoingWsMessage(
                WsMessageType.ROOM_ACTIVE,
                "123456"
            )
        )

        assertFalse(sentAfterRemove)
    }
}