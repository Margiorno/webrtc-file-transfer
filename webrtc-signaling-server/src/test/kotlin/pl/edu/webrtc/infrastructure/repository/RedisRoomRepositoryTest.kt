package pl.edu.webrtc.infrastructure.repository

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.keys.KeyCommands
import io.quarkus.redis.datasource.value.ValueCommands
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.edu.webrtc.domain.model.*

class RedisRoomRepositoryTest {

    private lateinit var redisDataSource: RedisDataSource
    private lateinit var valueCommands: ValueCommands<String, String>
    private lateinit var keyCommands: KeyCommands<String>
    private lateinit var repository: RedisRoomRepository

    private val objectMapper = ObjectMapper()
    private val ttlSeconds = 900L
    private val roomCode = RoomCode("123456")
    private val hostId = ClientId.generate()
    private val hostSecret = ClientSecret.generate()
    private val guestId = ClientId.generate()
    private val guestSecret = ClientSecret.generate()
    private val metadata = DeviceMetadata(DeviceType.DESKTOP_PC, OperatingSystem.WINDOWS, Browser.FIREFOX)

    @BeforeEach
    fun setUp() {
        redisDataSource = mockk()
        valueCommands = mockk(relaxed = true)
        keyCommands = mockk(relaxed = true)

        every { redisDataSource.value(String::class.java, String::class.java) } returns valueCommands
        every { redisDataSource.key(String::class.java) } returns keyCommands

        repository = RedisRoomRepository(redisDataSource, objectMapper, ttlSeconds)
    }

    @Test
    fun `save and findByCode should serialize and reconstruct full Room with 2FA challenge and metadata`() {
        val storedJsonSlot = slot<String>()
        justRun { valueCommands.setex(eq("webrtc:room:123456"), eq(ttlSeconds), capture(storedJsonSlot)) }

        val originalRoom = Room(roomCode, hostId, hostSecret, SecurityMode.TWO_FACTOR).also {
            it.requestJoin(guestId, guestSecret, metadata)
            it.resolveJoin(hostSecret, JoinDecision.ACCEPT)
        }

        repository.save(originalRoom)

        every { valueCommands.get("webrtc:room:123456") } answers { storedJsonSlot.captured }

        val reconstructed = repository.findByCode(roomCode)

        assertNotNull(reconstructed)
        assertEquals(originalRoom.code, reconstructed!!.code)
        assertEquals(originalRoom.hostId, reconstructed.hostId)
        assertEquals(originalRoom.hostSecret, reconstructed.hostSecret)
        assertEquals(originalRoom.securityMode, reconstructed.securityMode)
        assertEquals(RoomState.PENDING_2FA, reconstructed.state)
        assertEquals(guestId, reconstructed.guestId)
        assertEquals(guestSecret, reconstructed.guestSecret)
        assertEquals(metadata, reconstructed.guestMetadata)
        assertEquals(originalRoom.challenge2FA, reconstructed.challenge2FA)
    }

    @Test
    fun `findByCode should return null when key does not exist in Redis`() {
        every { valueCommands.get("webrtc:room:999999") } returns null
        assertNull(repository.findByCode(RoomCode("999999")))
    }

    @Test
    fun `deleteByCode and existsByCode should delegate to Redis KeyCommands`() {
        every { keyCommands.exists("webrtc:room:123456") } returns true
        assertTrue(repository.existsByCode(roomCode))

        repository.deleteByCode(roomCode)
        verify(exactly = 1) { keyCommands.del("webrtc:room:123456") }
    }
}