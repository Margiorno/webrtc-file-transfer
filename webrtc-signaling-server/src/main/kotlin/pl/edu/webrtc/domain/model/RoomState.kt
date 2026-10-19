package pl.edu.webrtc.domain.model

enum class RoomState {
    WAITING_FOR_GUEST,
    PENDING_HOST_APPROVAL,
    PENDING_2FA,
    ACTIVE,
    CLOSED
}