package pl.edu.webrtc.application.service

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import pl.edu.webrtc.domain.model.*
import pl.edu.webrtc.domain.repository.RoomRepository

@ExtendWith(MockKExtension::class)
class RoomSignalingServiceTest {

    @MockK
    private lateinit var roomRepository: RoomRepository

    @InjectMockKs
    private lateinit var service: RoomSignalingService

    private val roomCode = RoomCode("123456")
    private val hostId = ClientId.generate()
    private val hostSecret = ClientSecret.generate()
    private val guestId = ClientId.generate()
    private val guestSecret = ClientSecret.generate()
    private val metadata = DeviceMetadata(DeviceType.DESKTOP_PC, OperatingSystem.WINDOWS, Browser.FIREFOX)

    private fun MockKMatcherScope.anyRoomCode(): RoomCode {
        val boxMethod = RoomCode::class.java.getDeclaredMethod("box-impl", String::class.java).apply {
            isAccessible = true
        }
        return boxMethod.invoke(null, any<String>()) as RoomCode
    }

    @Nested
    inner class CreateRoomTests {

        @Test
        fun `should create room in STANDARD mode and save it in repository`() {
            val roomSlot = slot<Room>()
            every { roomRepository.existsByCode(anyRoomCode()) } returns false
            justRun { roomRepository.save(capture(roomSlot)) }

            val createdRoom = service.createRoom(hostId, hostSecret, SecurityMode.STANDARD)

            assertEquals(hostId, createdRoom.hostId)
            assertEquals(hostSecret, createdRoom.hostSecret)
            assertEquals(SecurityMode.STANDARD, createdRoom.securityMode)
            assertEquals(RoomState.WAITING_FOR_GUEST, createdRoom.state)
            assertEquals(createdRoom, roomSlot.captured)

            verify(exactly = 1) { roomRepository.existsByCode(createdRoom.code) }
            verify(exactly = 1) { roomRepository.save(createdRoom) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should retry code generation when collisions occur and succeed when free code is found`() {
            every { roomRepository.existsByCode(anyRoomCode()) } returnsMany listOf(true, true, true, false)
            justRun { roomRepository.save(any()) }

            val createdRoom = service.createRoom(hostId, hostSecret, SecurityMode.TWO_FACTOR)

            assertEquals(RoomState.WAITING_FOR_GUEST, createdRoom.state)
            verify(exactly = 4) { roomRepository.existsByCode(anyRoomCode()) }
            verify(exactly = 1) { roomRepository.save(createdRoom) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should succeed on the 10th (last allowed) attempt after 9 collisions`() {
            val responses = List(9) { true } + false
            every { roomRepository.existsByCode(anyRoomCode()) } returnsMany responses
            justRun { roomRepository.save(any()) }

            val createdRoom = service.createRoom(hostId, hostSecret, SecurityMode.STANDARD)

            assertNotNull(createdRoom)
            verify(exactly = 10) { roomRepository.existsByCode(anyRoomCode()) }
            verify(exactly = 1) { roomRepository.save(createdRoom) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should throw IllegalStateException and never save when all 10 attempts collide`() {
            every { roomRepository.existsByCode(anyRoomCode()) } returns true

            val exception = assertThrows<IllegalStateException> {
                service.createRoom(hostId, hostSecret, SecurityMode.STANDARD)
            }

            assertTrue(exception.message!!.contains("Server is too busy"))
            verify(exactly = 10) { roomRepository.existsByCode(anyRoomCode()) }
            verify(exactly = 0) { roomRepository.save(any()) }
            confirmVerified(roomRepository)
        }
    }

    @Nested
    inner class RequestJoinTests {

        @Test
        fun `should transition room to PENDING_HOST_APPROVAL and save updated state`() {
            val existingRoom = Room(roomCode, hostId, hostSecret, SecurityMode.TWO_FACTOR)
            every { roomRepository.findByCode(roomCode) } returns existingRoom
            justRun { roomRepository.save(existingRoom) }

            val updatedRoom = service.requestJoin(roomCode, guestId, guestSecret, metadata)

            assertEquals(RoomState.PENDING_HOST_APPROVAL, updatedRoom.state)
            assertEquals(guestId, updatedRoom.guestId)
            assertEquals(guestSecret, updatedRoom.guestSecret)
            assertEquals(metadata, updatedRoom.guestMetadata)

            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            verify(exactly = 1) { roomRepository.save(existingRoom) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should throw NoSuchElementException and not save when room does not exist`() {
            every { roomRepository.findByCode(roomCode) } returns null

            assertThrows<NoSuchElementException> {
                service.requestJoin(roomCode, guestId, guestSecret, metadata)
            }

            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            verify(exactly = 0) { roomRepository.save(any()) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should throw IllegalStateException and not save when room is already occupied`() {
            val occupiedRoom = Room(roomCode, hostId, hostSecret, SecurityMode.STANDARD).also {
                it.requestJoin(guestId, guestSecret, metadata)
            }
            every { roomRepository.findByCode(roomCode) } returns occupiedRoom

            assertThrows<IllegalStateException> {
                service.requestJoin(roomCode, ClientId.generate(), ClientSecret.generate(), metadata)
            }

            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            verify(exactly = 0) { roomRepository.save(any()) }
            confirmVerified(roomRepository)
        }
    }

    @Nested
    inner class ResolveJoinTests {

        @Test
        fun `should transition to ACTIVE and save room when host accepts in STANDARD mode`() {
            val room = Room(roomCode, hostId, hostSecret, SecurityMode.STANDARD).also {
                it.requestJoin(guestId, guestSecret, metadata)
            }
            every { roomRepository.findByCode(roomCode) } returns room
            justRun { roomRepository.save(room) }

            val resolved = service.resolveJoin(roomCode, hostSecret, JoinDecision.ACCEPT)

            assertEquals(RoomState.ACTIVE, resolved.state)
            assertNull(resolved.challenge2FA)

            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            verify(exactly = 1) { roomRepository.save(room) }
            verify(exactly = 0) { roomRepository.deleteByCode(anyRoomCode()) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should transition to PENDING_2FA, generate challenge and save when host accepts in TWO_FACTOR mode`() {
            val room = Room(roomCode, hostId, hostSecret, SecurityMode.TWO_FACTOR).also {
                it.requestJoin(guestId, guestSecret, metadata)
            }
            every { roomRepository.findByCode(roomCode) } returns room
            justRun { roomRepository.save(room) }

            val resolved = service.resolveJoin(roomCode, hostSecret, JoinDecision.ACCEPT)

            assertEquals(RoomState.PENDING_2FA, resolved.state)
            assertNotNull(resolved.challenge2FA)

            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            verify(exactly = 1) { roomRepository.save(room) }
            verify(exactly = 0) { roomRepository.deleteByCode(anyRoomCode()) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should close room and delete from repository without saving when host rejects join`() {
            val room = Room(roomCode, hostId, hostSecret, SecurityMode.TWO_FACTOR).also {
                it.requestJoin(guestId, guestSecret, metadata)
            }
            every { roomRepository.findByCode(roomCode) } returns room
            justRun { roomRepository.deleteByCode(roomCode) }

            val resolved = service.resolveJoin(roomCode, hostSecret, JoinDecision.REJECT)

            assertEquals(RoomState.CLOSED, resolved.state)

            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            verify(exactly = 1) { roomRepository.deleteByCode(roomCode) }
            verify(exactly = 0) { roomRepository.save(any()) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should throw IllegalArgumentException and neither save nor delete when hostSecret is invalid`() {
            val room = Room(roomCode, hostId, hostSecret, SecurityMode.STANDARD).also {
                it.requestJoin(guestId, guestSecret, metadata)
            }
            every { roomRepository.findByCode(roomCode) } returns room

            assertThrows<IllegalArgumentException> {
                service.resolveJoin(roomCode, ClientSecret.generate(), JoinDecision.ACCEPT)
            }

            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            verify(exactly = 0) { roomRepository.save(any()) }
            verify(exactly = 0) { roomRepository.deleteByCode(anyRoomCode()) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should throw NoSuchElementException when resolving non-existent room`() {
            every { roomRepository.findByCode(roomCode) } returns null

            assertThrows<NoSuchElementException> {
                service.resolveJoin(roomCode, hostSecret, JoinDecision.ACCEPT)
            }

            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            confirmVerified(roomRepository)
        }
    }

    @Nested
    inner class Submit2FATests {

        @Test
        fun `should transition to ACTIVE and save room when guest submits correct 2FA code`() {
            val room = createRoomInPending2FA()
            val correctCode = room.challenge2FA!!.correctCode
            every { roomRepository.findByCode(roomCode) } returns room
            justRun { roomRepository.save(room) }

            val activeRoom = service.submit2FA(roomCode, guestId, guestSecret, correctCode)

            assertEquals(RoomState.ACTIVE, activeRoom.state)
            assertTrue(activeRoom.challenge2FA!!.isSuccessful())

            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            verify(exactly = 1) { roomRepository.save(room) }
            verify(exactly = 0) { roomRepository.deleteByCode(anyRoomCode()) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should close room and delete from repository on wrong 2FA code (Burn on Fail)`() {
            val room = createRoomInPending2FA()
            val challenge = room.challenge2FA!!
            val wrongCode = challenge.options.first { it != challenge.correctCode }
            every { roomRepository.findByCode(roomCode) } returns room
            justRun { roomRepository.deleteByCode(roomCode) }

            val burnedRoom = service.submit2FA(roomCode, guestId, guestSecret, wrongCode)

            assertEquals(RoomState.CLOSED, burnedRoom.state)
            assertFalse(burnedRoom.challenge2FA!!.isSuccessful())

            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            verify(exactly = 1) { roomRepository.deleteByCode(roomCode) }
            verify(exactly = 0) { roomRepository.save(any()) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should throw IllegalArgumentException and not modify repository when guest credentials are invalid`() {
            val room = createRoomInPending2FA()
            val correctCode = room.challenge2FA!!.correctCode
            every { roomRepository.findByCode(roomCode) } returns room

            assertThrows<IllegalArgumentException> {
                service.submit2FA(roomCode, guestId, ClientSecret.generate(), correctCode)
            }

            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            verify(exactly = 0) { roomRepository.save(any()) }
            verify(exactly = 0) { roomRepository.deleteByCode(anyRoomCode()) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should throw NoSuchElementException when submitting 2FA to non-existent room`() {
            every { roomRepository.findByCode(roomCode) } returns null

            assertThrows<NoSuchElementException> {
                service.submit2FA(roomCode, guestId, guestSecret, TwoFactorCode(123))
            }

            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            confirmVerified(roomRepository)
        }

        private fun createRoomInPending2FA(): Room {
            return Room(roomCode, hostId, hostSecret, SecurityMode.TWO_FACTOR).also {
                it.requestJoin(guestId, guestSecret, metadata)
                it.resolveJoin(hostSecret, JoinDecision.ACCEPT)
            }
        }
    }

    @Nested
    inner class ResolveSignalingRecipientTests {

        @Test
        fun `should return guestId when host sends signaling in ACTIVE room`() {
            val activeRoom = createActiveRoom()
            every { roomRepository.findByCode(roomCode) } returns activeRoom

            val recipient = service.resolveSignalingRecipient(roomCode, hostId, hostSecret)

            assertEquals(guestId, recipient)
            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should return hostId when guest sends signaling in ACTIVE room`() {
            val activeRoom = createActiveRoom()
            every { roomRepository.findByCode(roomCode) } returns activeRoom

            val recipient = service.resolveSignalingRecipient(roomCode, guestId, guestSecret)

            assertEquals(hostId, recipient)
            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should throw IllegalArgumentException when room is not ACTIVE`() {
            val waitingRoom = Room(roomCode, hostId, hostSecret, SecurityMode.STANDARD)
            every { roomRepository.findByCode(roomCode) } returns waitingRoom

            assertThrows<IllegalArgumentException> {
                service.resolveSignalingRecipient(roomCode, hostId, hostSecret)
            }

            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should throw IllegalArgumentException when sender credentials are invalid`() {
            val activeRoom = createActiveRoom()
            every { roomRepository.findByCode(roomCode) } returns activeRoom

            assertThrows<IllegalArgumentException> {
                service.resolveSignalingRecipient(roomCode, hostId, ClientSecret.generate())
            }

            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should throw NoSuchElementException when room does not exist`() {
            every { roomRepository.findByCode(roomCode) } returns null

            assertThrows<NoSuchElementException> {
                service.resolveSignalingRecipient(roomCode, hostId, hostSecret)
            }

            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            confirmVerified(roomRepository)
        }

        private fun createActiveRoom(): Room {
            return Room(roomCode, hostId, hostSecret, SecurityMode.STANDARD).also {
                it.requestJoin(guestId, guestSecret, metadata)
                it.resolveJoin(hostSecret, JoinDecision.ACCEPT)
            }
        }
    }

    @Nested
    inner class VerifyParticipantTests {

        @Test
        fun `should return room when valid host credentials are provided`() {
            val room = Room(roomCode, hostId, hostSecret, SecurityMode.STANDARD)
            every { roomRepository.findByCode(roomCode) } returns room

            val result = service.verifyParticipant(roomCode, hostId, hostSecret)

            assertEquals(room, result)
            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should return room when valid guest credentials are provided`() {
            val room = Room(roomCode, hostId, hostSecret, SecurityMode.STANDARD).also {
                it.requestJoin(guestId, guestSecret, metadata)
            }
            every { roomRepository.findByCode(roomCode) } returns room

            val result = service.verifyParticipant(roomCode, guestId, guestSecret)

            assertEquals(room, result)
            verify(exactly = 1) { roomRepository.findByCode(roomCode) }
            confirmVerified(roomRepository)
        }

        @Test
        fun `should throw IllegalArgumentException when credentials do not match host or guest`() {
            val room = Room(roomCode, hostId, hostSecret, SecurityMode.STANDARD).also {
                it.requestJoin(guestId, guestSecret, metadata)
            }
            every { roomRepository.findByCode(roomCode) } returns room

            assertThrows<IllegalArgumentException> {
                service.verifyParticipant(roomCode, hostId, ClientSecret.generate())
            }
            assertThrows<IllegalArgumentException> {
                service.verifyParticipant(roomCode, guestId, ClientSecret.generate())
            }
            assertThrows<IllegalArgumentException> {
                service.verifyParticipant(roomCode, ClientId.generate(), hostSecret)
            }
        }

        @Test
        fun `should throw NoSuchElementException when room does not exist`() {
            every { roomRepository.findByCode(roomCode) } returns null

            assertThrows<NoSuchElementException> {
                service.verifyParticipant(roomCode, hostId, hostSecret)
            }
        }
    }
}