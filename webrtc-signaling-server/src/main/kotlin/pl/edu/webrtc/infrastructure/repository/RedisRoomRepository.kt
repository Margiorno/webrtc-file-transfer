package pl.edu.webrtc.infrastructure.repository

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.keys.KeyCommands
import io.quarkus.redis.datasource.value.ValueCommands
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import org.eclipse.microprofile.config.inject.ConfigProperty
import pl.edu.webrtc.domain.model.*
import pl.edu.webrtc.domain.repository.RoomRepository

@ApplicationScoped
@Alternative
@Priority(1)
class RedisRoomRepository(
    redisDataSource: RedisDataSource,
    private val objectMapper: ObjectMapper,
    @ConfigProperty(name = "webrtc.room.ttl-seconds", defaultValue = "900")
    private val roomTtlSeconds: Long
) : RoomRepository {

    private val valueCommands: ValueCommands<String, String> =
        redisDataSource.value(String::class.java, String::class.java)
    private val keyCommands: KeyCommands<String> =
        redisDataSource.key(String::class.java)

    private fun key(code: RoomCode): String = "webrtc:room:${code.value}"

    override fun save(room: Room) {
        val dto = RedisRoomDto.fromDomain(room)
        val json = objectMapper.writeValueAsString(dto)
        valueCommands.setex(key(room.code), roomTtlSeconds, json)
    }

    override fun findByCode(code: RoomCode): Room? {
        val json = valueCommands.get(key(code)) ?: return null
        val dto = objectMapper.readValue(json, RedisRoomDto::class.java)
        return dto.toDomain()
    }

    override fun deleteByCode(code: RoomCode) {
        keyCommands.del(key(code))
    }

    override fun existsByCode(code: RoomCode): Boolean {
        return keyCommands.exists(key(code))
    }
}

data class RedisRoomDto(
    var code: String = "",
    var hostId: String = "",
    var hostSecret: String = "",
    var securityMode: SecurityMode = SecurityMode.STANDARD,
    var state: RoomState = RoomState.WAITING_FOR_GUEST,
    var guestId: String? = null,
    var guestSecret: String? = null,
    var guestDeviceType: DeviceType? = null,
    var guestOs: OperatingSystem? = null,
    var guestBrowser: Browser? = null,
    var challengeOptions: List<Int>? = null,
    var challengeCorrectCode: Int? = null,
    var challengeGuestAnswer: Int? = null
) {
    fun toDomain(): Room {
        val metadata = if (guestDeviceType != null && guestOs != null && guestBrowser != null) {
            DeviceMetadata(guestDeviceType!!, guestOs!!, guestBrowser!!)
        } else null

        val challenge = if (challengeOptions != null && challengeCorrectCode != null) {
            TwoFactorChallenge(
                options = challengeOptions!!.map { TwoFactorCode(it) },
                correctCode = TwoFactorCode(challengeCorrectCode!!),
                guestAnswer = challengeGuestAnswer?.let { TwoFactorCode(it) }
            )
        } else null

        return Room.reconstruct(
            code = RoomCode(code),
            hostId = ClientId.fromString(hostId),
            hostSecret = ClientSecret(hostSecret),
            securityMode = securityMode,
            state = state,
            guestId = guestId?.let { ClientId.fromString(it) },
            guestSecret = guestSecret?.let { ClientSecret(it) },
            guestMetadata = metadata,
            challenge2FA = challenge
        )
    }

    companion object {
        fun fromDomain(room: Room): RedisRoomDto {
            return RedisRoomDto(
                code = room.code.value,
                hostId = room.hostId.value.toString(),
                hostSecret = room.hostSecret.value,
                securityMode = room.securityMode,
                state = room.state,
                guestId = room.guestId?.value?.toString(),
                guestSecret = room.guestSecret?.value,
                guestDeviceType = room.guestMetadata?.deviceType,
                guestOs = room.guestMetadata?.operatingSystem,
                guestBrowser = room.guestMetadata?.browser,
                challengeOptions = room.challenge2FA?.options?.map { it.value },
                challengeCorrectCode = room.challenge2FA?.correctCode?.value,
                challengeGuestAnswer = room.challenge2FA?.guestAnswer?.value
            )
        }
    }
}