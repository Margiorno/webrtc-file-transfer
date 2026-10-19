package pl.edu.webrtc.infrastructure.websocket

import pl.edu.webrtc.domain.model.DeviceMetadata
import pl.edu.webrtc.domain.model.RoomState

enum class WsMessageType {
    AUTH,

    SDP_OFFER,
    SDP_ANSWER,
    ICE_CANDIDATE,

    AUTH_SUCCESS,
    GUEST_JOIN_REQUESTED,
    TWO_FACTOR_CHALLENGE,
    ROOM_ACTIVE,
    ROOM_CLOSED,
    PEER_DISCONNECTED,
    ERROR
}

data class IncomingWsMessage(
    val type: WsMessageType,
    val clientId: String,
    val clientSecret: String,
    val payload: Any? = null
){
    constructor() : this(WsMessageType.AUTH, "", "", null)
}

data class OutgoingWsMessage(
    val type: WsMessageType,
    val roomCode: String,
    val state: RoomState? = null,
    val payload: Any? = null
)

data class GuestJoinRequestedPayload(
    val guestId: String,
    val deviceMetadata: DeviceMetadata
)

data class TwoFactorOptionsPayload(
    val options: List<Int>
)

data class WsErrorPayload(
    val message: String
)