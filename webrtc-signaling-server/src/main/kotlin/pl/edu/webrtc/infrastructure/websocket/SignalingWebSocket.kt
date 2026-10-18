package pl.edu.webrtc.infrastructure.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.websockets.next.*
import org.jboss.logging.Logger
import pl.edu.webrtc.application.service.RoomSignalingService
import pl.edu.webrtc.domain.model.ClientId
import pl.edu.webrtc.domain.model.ClientSecret
import pl.edu.webrtc.domain.model.RoomCode

@WebSocket(path = "/ws/signaling/{code}")
class SignalingWebSocket(
    private val signalingService: RoomSignalingService,
    private val sessionRegistry: WebSocketSessionRegistry,
    private val objectMapper: ObjectMapper
) {
    private val logger =
        Logger.getLogger(SignalingWebSocket::class.java)

    @OnTextMessage
    fun onMessage(
        @PathParam("code") rawCode: String,
        rawMessage: String,
        connection: WebSocketConnection
    ) {
        try {
            val roomCode = RoomCode(rawCode)
            val message = parseMessage(rawMessage)
            val senderId = ClientId.fromString(message.clientId)
            val secret = ClientSecret(message.clientSecret)

            dispatchMessage(
                roomCode,
                senderId,
                secret,
                message,
                connection
            )
        } catch (e: Exception) {
            logger.warn("WebSocket error in room $rawCode: ${e.message}")
            sendError(
                connection,
                rawCode,
                e.message ?: "Invalid signaling message"
            )
        }
    }

    @OnClose
    fun onClose(connection: WebSocketConnection) {
        sessionRegistry.unregisterByConnectionId(connection.id())
    }

    private fun parseMessage(rawMessage: String): IncomingWsMessage {
        return objectMapper.readValue(
            rawMessage,
            IncomingWsMessage::class.java
        )
    }

    private fun dispatchMessage(
        roomCode: RoomCode,
        senderId: ClientId,
        secret: ClientSecret,
        message: IncomingWsMessage,
        connection: WebSocketConnection
    ) {
        when (message.type) {
            WsMessageType.AUTH ->
                handleAuth(roomCode, senderId, secret, connection)

            WsMessageType.SDP_OFFER,
            WsMessageType.SDP_ANSWER,
            WsMessageType.ICE_CANDIDATE ->
                handleSignaling(roomCode, senderId, secret, message)

            else -> sendError(
                connection,
                roomCode.value,
                "Unsupported incoming message type: ${message.type}"
            )
        }
    }

    private fun handleAuth(
        roomCode: RoomCode,
        senderId: ClientId,
        secret: ClientSecret,
        connection: WebSocketConnection
    ) {
        val room = signalingService.verifyParticipant(
            roomCode,
            senderId,
            secret
        )

        sessionRegistry.register(senderId, connection)

        sessionRegistry.sendToClient(
            senderId,
            OutgoingWsMessage(
                type = WsMessageType.AUTH_SUCCESS,
                roomCode = room.code.value,
                state = room.state
            )
        )
    }

    private fun handleSignaling(
        roomCode: RoomCode,
        senderId: ClientId,
        secret: ClientSecret,
        message: IncomingWsMessage
    ) {
        val recipientId = signalingService.resolveSignalingRecipient(
            roomCode,
            senderId,
            secret
        )

        sessionRegistry.sendToClient(
            recipientId,
            OutgoingWsMessage(
                type = message.type,
                roomCode = roomCode.value,
                payload = message.payload
            )
        )
    }

    private fun sendError(
        connection: WebSocketConnection,
        roomCode: String,
        errorMessage: String
    ) {
        if (!connection.isOpen) return

        val errorMsg = OutgoingWsMessage(
            type = WsMessageType.ERROR,
            roomCode = roomCode,
            payload = WsErrorPayload(errorMessage)
        )

        connection.sendTextAndAwait(
            objectMapper.writeValueAsString(errorMsg)
        )
    }
}
