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
import pl.edu.webrtc.domain.model.SecurityMode
import pl.edu.webrtc.domain.model.TwoFactorCode

@Path("/api/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class RoomController(
    private val signalingService: RoomSignalingService
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

        val response = RoomStateResponse(roomCode = room.code.value, state = room.state)
        return Response.ok(response).build()
    }
}