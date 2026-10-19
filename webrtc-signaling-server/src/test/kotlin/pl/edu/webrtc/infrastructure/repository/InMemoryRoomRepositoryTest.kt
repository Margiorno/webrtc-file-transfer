package pl.edu.webrtc.infrastructure.repository

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.edu.webrtc.domain.model.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class InMemoryRoomRepositoryTest {

    private lateinit var repository: InMemoryRoomRepository

    private val roomCode = RoomCode("123456")
    private val hostId = ClientId.generate()
    private val hostSecret = ClientSecret.generate()

    @BeforeEach
    fun setUp() {
        repository = InMemoryRoomRepository()
    }

    private fun createSampleRoom(
        code: RoomCode = roomCode,
        mode: SecurityMode = SecurityMode.TWO_FACTOR
    ): Room {
        return Room(code, hostId, hostSecret, mode)
    }

    @Test
    fun `save and findByCode should store and retrieve room`() {
        val room = createSampleRoom()

        repository.save(room)
        val found = repository.findByCode(roomCode)

        assertNotNull(found)
        assertEquals(room, found)
    }

    @Test
    fun `findByCode should return null when room does not exist`() {
        val found = repository.findByCode(RoomCode("999999"))
        assertNull(found)
    }

    @Test
    fun `save should overwrite existing room with the same code`() {
        val initialRoom = createSampleRoom(mode = SecurityMode.STANDARD)
        repository.save(initialRoom)

        val updatedRoom = createSampleRoom(mode = SecurityMode.TWO_FACTOR)
        updatedRoom.requestJoin(
            guestId = ClientId.generate(),
            guestSecret = ClientSecret.generate(),
            metadata = DeviceMetadata(DeviceType.DESKTOP_PC, OperatingSystem.WINDOWS, Browser.FIREFOX)
        )
        repository.save(updatedRoom)

        val found = repository.findByCode(roomCode)
        assertEquals(SecurityMode.TWO_FACTOR, found?.securityMode)
        assertEquals(RoomState.PENDING_HOST_APPROVAL, found?.state)
    }

    @Test
    fun `existsByCode should return true for saved room and false otherwise`() {
        assertFalse(repository.existsByCode(roomCode))

        repository.save(createSampleRoom())

        assertTrue(repository.existsByCode(roomCode))
        assertFalse(repository.existsByCode(RoomCode("654321")))
    }

    @Test
    fun `deleteByCode should remove existing room`() {
        repository.save(createSampleRoom())
        assertTrue(repository.existsByCode(roomCode))

        repository.deleteByCode(roomCode)

        assertFalse(repository.existsByCode(roomCode))
        assertNull(repository.findByCode(roomCode))
    }

    @Test
    fun `deleteByCode should not throw exception when deleting non-existent room`() {
        assertDoesNotThrow {
            repository.deleteByCode(RoomCode("000000"))
        }
    }

    @Test
    fun `repository should handle concurrent writes and reads safely`() {
        val threadCount = 16
        val roomsCount = 500
        val executor = Executors.newFixedThreadPool(threadCount)

        try {
            val tasks = (1..roomsCount).map { index ->
                Callable {
                    val formattedCode = index.toString().padStart(6, '0')
                    val code = RoomCode(formattedCode)
                    val room = createSampleRoom(code = code)
                    repository.save(room)
                    assertTrue(repository.existsByCode(code))
                    assertNotNull(repository.findByCode(code))
                }
            }

            val futures = executor.invokeAll(tasks)
            futures.forEach { it.get() }

            for (index in 1..roomsCount) {
                val code = RoomCode(index.toString().padStart(6, '0'))
                assertTrue(repository.existsByCode(code))
            }
        } finally {
            executor.shutdownNow()
        }
    }
}