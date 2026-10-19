package pl.edu.webrtc.infrastructure.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import io.quarkus.websockets.next.WebSocketConnection
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.edu.webrtc.domain.model.ClientId
import pl.edu.webrtc.domain.model.RoomCode
import pl.edu.webrtc.domain.model.RoomState

class WebSocketSessionRegistryTest {

    private lateinit var registry: WebSocketSessionRegistry
    private val objectMapper = ObjectMapper()
    private val roomCode = RoomCode("123456")

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

        registry.register(clientId, roomCode, connection)

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

        registry.register(clientId, roomCode, connection)

        val sent = registry.sendToClient(
            clientId,
            OutgoingWsMessage(WsMessageType.ROOM_ACTIVE, "123456")
        )

        assertFalse(sent)
        verify(exactly = 0) { connection.sendTextAndAwait(any<String>()) }
    }

    @Test
    fun `unregisterByConnectionId should remove session and return DisconnectedSession`() {
        val clientId = ClientId.generate()
        val connection = mockk<WebSocketConnection>()
        every { connection.id() } returns "conn-123"

        registry.register(clientId, roomCode, connection)

        val disconnected = registry.unregisterByConnectionId("conn-123")
        assertEquals(DisconnectedSession(clientId, roomCode), disconnected)

        val sentAfterRemove = registry.sendToClient(
            clientId,
            OutgoingWsMessage(
                WsMessageType.ROOM_ACTIVE,
                "123456"
            )
        )
        assertFalse(sentAfterRemove)
    }

    @Test
    fun `registering new connection for same client should replace old connection`() {
        val clientId = ClientId.generate()
        val oldConn = mockk<WebSocketConnection>()
        val newConn = mockk<WebSocketConnection>()
        every { oldConn.id() } returns "conn-old"
        every { newConn.id() } returns "conn-new"

        registry.register(clientId, roomCode, oldConn)
        registry.register(clientId, roomCode, newConn)

        assertNull(registry.unregisterByConnectionId("conn-old"))
        assertEquals(DisconnectedSession(clientId, roomCode), registry.unregisterByConnectionId("conn-new"))
    }
}