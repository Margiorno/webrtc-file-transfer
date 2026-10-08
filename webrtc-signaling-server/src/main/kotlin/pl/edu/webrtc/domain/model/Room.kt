package pl.edu.webrtc.domain.model

class Room(
    val code: RoomCode,
    val hostId: ClientId,
    val hostSecret: ClientSecret,
    val securityMode: SecurityMode
) {
    var state: RoomState = RoomState.WAITING_FOR_GUEST
    private set

    var guestId: ClientId? = null
    private set

    var guestSecret: ClientSecret? = null
    private set

    var guestMetadata: DeviceMetadata? = null
    private set

    var challenge2FA: TwoFactorChallenge? = null
    private set

    fun requestJoin(
        guestId: ClientId,
        guestSecret: ClientSecret,
        metadata: DeviceMetadata,
    ) {
        check(state == RoomState.WAITING_FOR_GUEST) {"Cannot join room in state $state"}

        this.guestId = guestId
        this.guestSecret = guestSecret
        this.guestMetadata = metadata
        this.state = RoomState.PENDING_HOST_APPROVAL
    }

    fun resolveJoin(secret: ClientSecret, decision: JoinDecision) {
        require(secret == hostSecret) { "Only host can resolve join request" }
        check(state == RoomState.WAITING_FOR_GUEST) {"Cannot resolve room in state $state"}

        if (decision!= JoinDecision.ACCEPT){
            this.state = RoomState.CLOSED
            return
        }

        if (securityMode == SecurityMode.TWO_FACTOR) {
            this.challenge2FA = TwoFactorChallenge.generate()
            this.state = RoomState.PENDING_2FA
        } else {
            this.state = RoomState.ACTIVE
        }
    }

    fun submit2FA(clientId: ClientId, secret: ClientSecret, code: TwoFactorCode) {
        check(state == RoomState.PENDING_2FA) { "Room is not waiting for 2FA" }
        require(clientId == guestId && secret == guestSecret) { "Only guest can submit 2FA" }

        val currentChallenge = challenge2FA ?: error("2FA challenge not initialized")
        val updatedChallenge = currentChallenge.withGuestAnswer(code)

        this.challenge2FA = updatedChallenge

        if (updatedChallenge.isSuccessful()) {
            this.state = RoomState.ACTIVE
        } else {
            this.state = RoomState.CLOSED
        }
    }

    fun canRouteSignaling(senderId: ClientId, secret: ClientSecret): Boolean {
        if (state != RoomState.ACTIVE) return false

        return (senderId == hostId && secret == hostSecret) ||
                (senderId == guestId && secret == guestSecret)
    }

    fun getRecipientId(senderId: ClientId): ClientId? {
        return when (senderId) {
            hostId -> guestId
            guestId -> hostId
            else -> null
        }
    }
}