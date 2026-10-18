package pl.edu.webrtc.infrastructure.web

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import pl.edu.webrtc.application.service.RoomSignalingService
import pl.edu.webrtc.domain.model.ClientId
import pl.edu.webrtc.domain.model.ClientSecret
import pl.edu.webrtc.domain.model.RoomCode
import pl.edu.webrtc.domain.model.RoomState
import pl.edu.webrtc.domain.model.SecurityMode
import pl.edu.webrtc.domain.model.TwoFactorCode
import pl.edu.webrtc.infrastructure.websocket.GuestJoinRequestedPayload
import pl.edu.webrtc.infrastructure.websocket.OutgoingWsMessage
import pl.edu.webrtc.infrastructure.websocket.TwoFactorOptionsPayload
import pl.edu.webrtc.infrastructure.websocket.WebSocketSessionRegistry
import pl.edu.webrtc.infrastructure.websocket.WsMessageType

@Path("/api/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class RoomController(
    private val signalingService: RoomSignalingService,
    private val sessionRegistry: WebSocketSessionRegistry
) {
    @POST
    fun createRoom(request: CreateRoomRequest?): Response {
        val securityMode = request?.securityMode ?: SecurityMode.STANDARD
        val hostId = ClientId.generate()
        val hostSecret = ClientSecret.generate()

        val room = signalingService.createRoom(hostId, hostSecret, securityMode)

        val response = RoomCreatedResponse(
            roomCode = room.code.value,
            hostId = room.hostId.value.toString(),
            hostSecret = room.hostSecret.value,
            state = room.state,
            securityMode = room.securityMode
        )

        return Response.status(Response.Status.CREATED).entity(response).build()
    }

    @POST
    @Path("/{code}/join")
    @Consumes(MediaType.WILDCARD)
    fun requestJoin(
        @PathParam("code") rawCode: String,
        @HeaderParam("User-Agent") userAgent: String?
    ): Response {
        val roomCode = RoomCode(rawCode)
        val guestId = ClientId.generate()
        val guestSecret = ClientSecret.generate()
        val metadata = UserAgentParser.parse(userAgent)

        val room = signalingService.requestJoin(roomCode, guestId, guestSecret, metadata)

        sessionRegistry.sendToClient(
            room.hostId,
            OutgoingWsMessage(
                type = WsMessageType.GUEST_JOIN_REQUESTED,
                roomCode = room.code.value,
                state = room.state,
                payload = GuestJoinRequestedPayload(
                    guestId = guestId.value.toString(),
                    deviceMetadata = metadata
                )
            )
        )

        val response = JoinRoomResponse(
            roomCode = room.code.value,
            guestId = guestId.value.toString(),
            guestSecret = guestSecret.value,
            state = room.state
        )

        return Response.ok(response).build()
    }

    @POST
    @Path("/{code}/resolve")
    fun resolveJoin(
        @PathParam("code") rawCode: String,
        @HeaderParam("X-Client-Secret") rawHostSecret: String?,
        request: ResolveJoinRequest?
    ): Response {
        require(!rawHostSecret.isNullOrBlank()) { "Missing X-Client-Secret header" }
        requireNotNull(request) { "Request body is required" }

        val roomCode = RoomCode(rawCode)
        val hostSecret = ClientSecret(rawHostSecret)

        val room = signalingService.resolveJoin(roomCode, hostSecret, request.decision)

        room.guestId?.let { guestId ->
            when (room.state) {
                RoomState.CLOSED -> sessionRegistry.sendToClient(
                    guestId,
                    OutgoingWsMessage(WsMessageType.ROOM_CLOSED, room.code.value, room.state)
                )
                RoomState.PENDING_2FA -> {
                    val options = room.challenge2FA?.options?.map { it.value }.orEmpty()
                    sessionRegistry.sendToClient(
                        guestId,
                        OutgoingWsMessage(
                            type = WsMessageType.TWO_FACTOR_CHALLENGE,
                            roomCode = room.code.value,
                            state = room.state,
                            payload = TwoFactorOptionsPayload(options)
                        )
                    )
                }
                RoomState.ACTIVE -> sessionRegistry.sendToClient(
                    guestId,
                    OutgoingWsMessage(WsMessageType.ROOM_ACTIVE, room.code.value, room.state)
                )
                else -> {}
            }
        }

        val response = ResolveJoinHostResponse(
            roomCode = room.code.value,
            state = room.state,
            twoFactorCorrectCode = room.challenge2FA?.correctCode?.value
        )
        return Response.ok(response).build()
    }

    @POST
    @Path("/{code}/2fa")
    fun submit2FA(
        @PathParam("code") rawCode: String,
        @HeaderParam("X-Client-Id") rawGuestId: String?,
        @HeaderParam("X-Client-Secret") rawGuestSecret: String?,
        request: Submit2FARequest?
    ): Response {
        require(!rawGuestId.isNullOrBlank()) { "Missing X-Client-Id header" }
        require(!rawGuestSecret.isNullOrBlank()) { "Missing X-Client-Secret header" }
        requireNotNull(request) { "Request body is required" }

        val roomCode = RoomCode(rawCode)
        val guestId = ClientId.fromString(rawGuestId)
        val guestSecret = ClientSecret(rawGuestSecret)
        val twoFactorCode = TwoFactorCode(request.code)

        val room = signalingService.submit2FA(roomCode, guestId, guestSecret, twoFactorCode)

        val eventType = if (room.state == RoomState.ACTIVE) {
            WsMessageType.ROOM_ACTIVE
        } else {
            WsMessageType.ROOM_CLOSED
        }

        sessionRegistry.sendToClient(
            room.hostId,
            OutgoingWsMessage(
                type = eventType,
                roomCode = room.code.value,
                state = room.state
            )
        )

        val response = RoomStateResponse(roomCode = room.code.value, state = room.state)
        return Response.ok(response).build()
    }
}