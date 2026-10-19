package pl.edu.webrtc.infrastructure.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.websockets.next.WebSocketConnection
import jakarta.enterprise.context.ApplicationScoped
import pl.edu.webrtc.domain.model.ClientId
import pl.edu.webrtc.domain.model.RoomCode
import java.util.concurrent.ConcurrentHashMap

data class DisconnectedSession(
    val clientId: ClientId,
    val roomCode: RoomCode
)

@ApplicationScoped
class WebSocketSessionRegistry(
    private val objectMapper: ObjectMapper
) {
    private val sessionsByClient =
        ConcurrentHashMap<String, WebSocketConnection>()
    private val sessionsByConnectionId =
        ConcurrentHashMap<String, DisconnectedSession>()

    fun register(
        clientId: ClientId,
        roomCode: RoomCode,
        connection: WebSocketConnection
    ) {
        val rawClientId = clientId.value.toString()
        val connectionId = connection.id()

        val previousConnection =
            sessionsByClient.put(rawClientId, connection)
        sessionsByConnectionId[connectionId] = DisconnectedSession(clientId, roomCode)

        if (previousConnection != null &&
            previousConnection.id() != connectionId
        ) {
            sessionsByConnectionId.remove(previousConnection.id())
        }
    }

    fun unregisterByConnectionId(connectionId: String): DisconnectedSession? {
        val disconnected = sessionsByConnectionId.remove(connectionId)
            ?: return null
        val rawClientId = disconnected.clientId.value.toString()

        var removedActiveConnection = false
        sessionsByClient.computeIfPresent(rawClientId) { _, currentConnection ->
            if (currentConnection.id() == connectionId) {
                removedActiveConnection = true
                null
            } else {
                currentConnection
            }
        }

        return if (removedActiveConnection) disconnected else null
    }

    fun sendToClient(
        clientId: ClientId,
        message: OutgoingWsMessage
    ): Boolean {
        val rawClientId = clientId.value.toString()
        val connection = sessionsByClient[rawClientId]
            ?: return false

        if (!connection.isOpen) {
            unregisterByConnectionId(connection.id())
            return false
        }

        val json = objectMapper.writeValueAsString(message)
        connection.sendTextAndAwait(json)
        return true
    }
}