package pl.edu.webrtc.infrastructure.web

import pl.edu.webrtc.domain.model.JoinDecision
import pl.edu.webrtc.domain.model.RoomState
import pl.edu.webrtc.domain.model.SecurityMode

data class CreateRoomRequest(
    val securityMode: SecurityMode = SecurityMode.STANDARD
)

data class RoomCreatedResponse(
    val roomCode: String,
    val hostId: String,
    val hostSecret: String,
    val state: RoomState,
    val securityMode: SecurityMode
)

data class JoinRoomResponse(
    val roomCode: String,
    val guestId: String,
    val guestSecret: String,
    val state: RoomState
)

data class ResolveJoinRequest(
    val decision: JoinDecision
)

data class ResolveJoinHostResponse(
    val roomCode: String,
    val state: RoomState,
    val twoFactorCorrectCode: Int? = null
)

data class Submit2FARequest(
    val code: Int
)

data class RoomStateResponse(
    val roomCode: String,
    val state: RoomState,
    val twoFactorOptions: List<Int>? = null
)

data class ErrorResponse(
    val error: String,
    val message: String
)