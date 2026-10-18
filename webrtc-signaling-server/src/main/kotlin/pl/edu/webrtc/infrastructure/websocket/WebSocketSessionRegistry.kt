
package pl.edu.webrtc.infrastructure.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.websockets.next.WebSocketConnection
import jakarta.enterprise.context.ApplicationScoped
import pl.edu.webrtc.domain.model.ClientId
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class WebSocketSessionRegistry(
    private val objectMapper: ObjectMapper
) {
    private val sessionsByClient =
        ConcurrentHashMap<String, WebSocketConnection>()

    private val clientsByConnectionId =
        ConcurrentHashMap<String, String>()

    fun register(
        clientId: ClientId,
        connection: WebSocketConnection
    ) {
        val rawClientId = clientId.value.toString()
        val connectionId = connection.id()

        val previousConnection =
            sessionsByClient.put(rawClientId, connection)

        clientsByConnectionId[connectionId] = rawClientId

        if (previousConnection != null &&
            previousConnection.id() != connectionId
        ) {
            clientsByConnectionId.remove(
                previousConnection.id(),
                rawClientId
            )
        }
    }

    fun unregisterByConnectionId(connectionId: String) {
        val rawClientId = clientsByConnectionId.remove(connectionId)
            ?: return

        sessionsByClient.computeIfPresent(rawClientId) { _, currentConnection ->
            if (currentConnection.id() == connectionId) {
                null
            } else {
                currentConnection
            }
        }
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
