package pl.edu.webrtc.application.service

import jakarta.enterprise.context.ApplicationScoped
import pl.edu.webrtc.domain.model.ClientId
import pl.edu.webrtc.domain.model.ClientSecret
import pl.edu.webrtc.domain.model.DeviceMetadata
import pl.edu.webrtc.domain.model.JoinDecision
import pl.edu.webrtc.domain.model.Room
import pl.edu.webrtc.domain.model.RoomCode
import pl.edu.webrtc.domain.model.RoomState
import pl.edu.webrtc.domain.model.SecurityMode
import pl.edu.webrtc.domain.model.TwoFactorCode
import pl.edu.webrtc.domain.repository.RoomRepository

@ApplicationScoped
class RoomSignalingService (
    private val roomRepository: RoomRepository
){
    companion object {
        private const val MAX_CODE_GENERATION_ATTEMPTS = 10
    }

    fun createRoom(
        hostId: ClientId,
        hostSecret: ClientSecret,
        securityMode: SecurityMode
    ): Room {
        var code: RoomCode
        var attempts = 0

        do{
            check(attempts < MAX_CODE_GENERATION_ATTEMPTS) {
                "Server is too busy, could not generate a unique room code"
            }
            code = RoomCode.generate()
            attempts++
        } while (roomRepository.existsByCode(code))

        val room = Room(
            code = code,
            hostId = hostId,
            hostSecret = hostSecret,
            securityMode = securityMode
        )

        roomRepository.save(room)
        return room
    }

    fun requestJoin(
        code: RoomCode,
        guestId: ClientId,
        guestSecret: ClientSecret,
        metadata: DeviceMetadata
    ): Room {
        val room = getRoomOrThrow(code)
        room.requestJoin(guestId, guestSecret, metadata)
        roomRepository.save(room)
        return room
    }

    fun resolveJoin(code: RoomCode, hostSecret: ClientSecret, decision: JoinDecision): Room {
        val room = getRoomOrThrow(code)
        room.resolveJoin(hostSecret, decision)

        if (room.state == RoomState.CLOSED){
            roomRepository.deleteByCode(code)
        } else {
            roomRepository.save(room)
        }
        return room
    }

    fun submit2FA(
        code: RoomCode,
        guestId: ClientId,
        guestSecret: ClientSecret,
        twoFactorCode: TwoFactorCode
    ): Room {
        val room = getRoomOrThrow(code)
        room.submit2FA(guestId, guestSecret, twoFactorCode)

        if (room.state == RoomState.CLOSED){
            roomRepository.deleteByCode(code)
        } else {
            roomRepository.save(room)
        }

        return room
    }

    fun resolveSignalingRecipient(
        code: RoomCode,
        senderId: ClientId,
        secret: ClientSecret
    ): ClientId {
        val room = getRoomOrThrow(code)
        require(room.canRouteSignaling(senderId, secret)) {
            "Unauthorized signaling attempt in room ${code.value}"
        }
        return room.getRecipientId(senderId)
            ?: error("Recipient not found in room ${code.value}")
    }

    private fun getRoomOrThrow(code: RoomCode): Room {
        return roomRepository.findByCode(code)
            ?: throw NoSuchElementException("Room with code ${code.value} was not found")
    }

    fun verifyParticipant(
        code: RoomCode,
        clientId: ClientId,
        secret: ClientSecret
    ): Room {
        val room = getRoomOrThrow(code)
        val isHost = room.hostId == clientId && room.hostSecret == secret
        val isGuest = room.guestId == clientId && room.guestSecret == secret

        require(isHost || isGuest) {
            "Invalid client credentials for room ${code.value}"
        }
        return room
    }
}