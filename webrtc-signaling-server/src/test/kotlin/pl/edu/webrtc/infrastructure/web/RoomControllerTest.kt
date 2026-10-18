package pl.edu.webrtc.infrastructure.web

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import pl.edu.webrtc.application.service.RoomSignalingService
import pl.edu.webrtc.domain.model.*
import pl.edu.webrtc.infrastructure.websocket.WebSocketSessionRegistry
import java.util.UUID

@ExtendWith(MockKExtension::class)
class RoomControllerTest {

    @MockK
    private lateinit var signalingService: RoomSignalingService

    @MockK(relaxed = true)
    private lateinit var sessionRegistry: WebSocketSessionRegistry

    @InjectMockKs
    private lateinit var controller: RoomController

    private val roomCode = RoomCode("123456")
    private val hostId = ClientId.generate()
    private val hostSecret = ClientSecret.generate()
    private val guestId = ClientId.generate()
    private val guestSecret = ClientSecret.generate()
    private val sampleMetadata = DeviceMetadata(
        DeviceType.DESKTOP_PC,
        OperatingSystem.WINDOWS,
        Browser.FIREFOX
    )

    private inline fun <reified T : Any, reified U : Any> MockKMatcherScope.anyInline(
        boxParamClass: Class<U>
    ): T {
        val boxMethod = T::class.java.getDeclaredMethod("box-impl", boxParamClass).apply {
            isAccessible = true
        }
        return boxMethod.invoke(null, any<U>()) as T
    }

    private fun MockKMatcherScope.anyRoomCode(): RoomCode =
        anyInline(String::class.java)

    private fun MockKMatcherScope.eqRoomCode(code: RoomCode): RoomCode {
        val boxMethod = RoomCode::class.java.getDeclaredMethod("box-impl", String::class.java).apply {
            isAccessible = true
        }
        return boxMethod.invoke(null, eq(code.value)) as RoomCode
    }

    private fun MockKMatcherScope.anyClientId(): ClientId =
        anyInline(UUID::class.java)

    private fun MockKMatcherScope.anyClientSecret(): ClientSecret =
        anyInline(String::class.java)

    private fun MockKMatcherScope.anyTwoFactorCode(): TwoFactorCode =
        anyInline(Int::class.javaPrimitiveType!!)

    @Nested
    inner class CreateRoomTests {

        @Test
        fun `should default to STANDARD security mode when request body is null`() {
            every {
                signalingService.createRoom(anyClientId(), anyClientSecret(), SecurityMode.STANDARD)
            } answers {
                val generatedHostId = ClientId(firstArg<UUID>())
                val generatedHostSecret = ClientSecret(secondArg<String>())
                Room(roomCode, generatedHostId, generatedHostSecret, SecurityMode.STANDARD)
            }

            val response = controller.createRoom(null)

            assertEquals(Response.Status.CREATED.statusCode, response.status)
            val body = response.entity as RoomCreatedResponse
            assertEquals("123456", body.roomCode)
            assertEquals(RoomState.WAITING_FOR_GUEST, body.state)
            assertEquals(SecurityMode.STANDARD, body.securityMode)
            assertEquals(32, body.hostSecret.length)
            assertDoesNotThrow { UUID.fromString(body.hostId) }

            verify(exactly = 1) {
                signalingService.createRoom(anyClientId(), anyClientSecret(), SecurityMode.STANDARD)
            }
            confirmVerified(signalingService)
        }

        @Test
        fun `should create room with explicitly requested TWO_FACTOR mode`() {
            every {
                signalingService.createRoom(anyClientId(), anyClientSecret(), SecurityMode.TWO_FACTOR)
            } answers {
                val generatedHostId = ClientId(firstArg<UUID>())
                val generatedHostSecret = ClientSecret(secondArg<String>())
                Room(roomCode, generatedHostId, generatedHostSecret, SecurityMode.TWO_FACTOR)
            }

            val response = controller.createRoom(CreateRoomRequest(SecurityMode.TWO_FACTOR))

            assertEquals(Response.Status.CREATED.statusCode, response.status)
            val body = response.entity as RoomCreatedResponse
            assertEquals("123456", body.roomCode)
            assertEquals(SecurityMode.TWO_FACTOR, body.securityMode)
            assertEquals(RoomState.WAITING_FOR_GUEST, body.state)

            verify(exactly = 1) {
                signalingService.createRoom(anyClientId(), anyClientSecret(), SecurityMode.TWO_FACTOR)
            }
            confirmVerified(signalingService)
        }
    }

    @Nested
    inner class RequestJoinTests {

        @Test
        fun `should parse User-Agent header, invoke service and return 200 OK with guest credentials`() {
            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0"
            val metadataSlot = slot<DeviceMetadata>()

            every {
                signalingService.requestJoin(
                    eqRoomCode(roomCode),
                    anyClientId(),
                    anyClientSecret(),
                    capture(metadataSlot)
                )
            } answers {
                val generatedGuestId = ClientId(secondArg<UUID>())
                val generatedGuestSecret = ClientSecret(thirdArg<String>())
                Room(roomCode, hostId, hostSecret, SecurityMode.STANDARD).also {
                    it.requestJoin(generatedGuestId, generatedGuestSecret, metadataSlot.captured)
                }
            }

            val response = controller.requestJoin("123456", userAgent)

            assertEquals(Response.Status.OK.statusCode, response.status)
            val body = response.entity as JoinRoomResponse
            assertEquals("123456", body.roomCode)
            assertEquals(RoomState.PENDING_HOST_APPROVAL, body.state)
            assertEquals(32, body.guestSecret.length)
            assertDoesNotThrow { UUID.fromString(body.guestId) }

            assertEquals(DeviceType.DESKTOP_PC, metadataSlot.captured.deviceType)
            assertEquals(OperatingSystem.WINDOWS, metadataSlot.captured.operatingSystem)
            assertEquals(Browser.FIREFOX, metadataSlot.captured.browser)

            verify(exactly = 1) {
                signalingService.requestJoin(eqRoomCode(roomCode), anyClientId(), anyClientSecret(), any())
            }
            confirmVerified(signalingService)
        }

        @Test
        fun `should handle null User-Agent header gracefully with UNKNOWN metadata`() {
            val metadataSlot = slot<DeviceMetadata>()

            every {
                signalingService.requestJoin(
                    eqRoomCode(roomCode),
                    anyClientId(),
                    anyClientSecret(),
                    capture(metadataSlot)
                )
            } answers {
                val generatedGuestId = ClientId(secondArg<UUID>())
                val generatedGuestSecret = ClientSecret(thirdArg<String>())
                Room(roomCode, hostId, hostSecret, SecurityMode.STANDARD).also {
                    it.requestJoin(generatedGuestId, generatedGuestSecret, metadataSlot.captured)
                }
            }

            val response = controller.requestJoin("123456", null)

            assertEquals(Response.Status.OK.statusCode, response.status)
            assertEquals(DeviceType.UNKNOWN, metadataSlot.captured.deviceType)
            assertEquals(OperatingSystem.UNKNOWN, metadataSlot.captured.operatingSystem)
            assertEquals(Browser.UNKNOWN, metadataSlot.captured.browser)

            verify(exactly = 1) {
                signalingService.requestJoin(eqRoomCode(roomCode), anyClientId(), anyClientSecret(), any())
            }
            confirmVerified(signalingService)
        }

        @Test
        fun `should throw IllegalArgumentException and never call service when roomCode path param is invalid`() {
            assertThrows<IllegalArgumentException> {
                controller.requestJoin("invalid-code", "Mozilla/5.0")
            }

            verify(exactly = 0) {
                signalingService.requestJoin(anyRoomCode(), anyClientId(), anyClientSecret(), any())
            }
            confirmVerified(signalingService)
        }
    }

    @Nested
    inner class ResolveJoinTests {

        @Test
        fun `should return ACTIVE state and null 2FA code when host accepts in STANDARD mode`() {
            val activeRoom = Room(roomCode, hostId, hostSecret, SecurityMode.STANDARD).also {
                it.requestJoin(guestId, guestSecret, sampleMetadata)
                it.resolveJoin(hostSecret, JoinDecision.ACCEPT)
            }
            every {
                signalingService.resolveJoin(roomCode, hostSecret, JoinDecision.ACCEPT)
            } returns activeRoom

            val response = controller.resolveJoin(
                rawCode = "123456",
                rawHostSecret = hostSecret.value,
                request = ResolveJoinRequest(JoinDecision.ACCEPT)
            )

            assertEquals(Response.Status.OK.statusCode, response.status)
            val body = response.entity as ResolveJoinHostResponse
            assertEquals("123456", body.roomCode)
            assertEquals(RoomState.ACTIVE, body.state)
            assertNull(body.twoFactorCorrectCode)

            verify(exactly = 1) {
                signalingService.resolveJoin(roomCode, hostSecret, JoinDecision.ACCEPT)
            }
            confirmVerified(signalingService)
        }

        @Test
        fun `should return PENDING_2FA state and correct 2FA code when host accepts in TWO_FACTOR mode`() {
            val pending2faRoom = Room(roomCode, hostId, hostSecret, SecurityMode.TWO_FACTOR).also {
                it.requestJoin(guestId, guestSecret, sampleMetadata)
                it.resolveJoin(hostSecret, JoinDecision.ACCEPT)
            }
            val expectedCorrectCode = pending2faRoom.challenge2FA!!.correctCode.value

            every {
                signalingService.resolveJoin(roomCode, hostSecret, JoinDecision.ACCEPT)
            } returns pending2faRoom

            val response = controller.resolveJoin(
                rawCode = "123456",
                rawHostSecret = hostSecret.value,
                request = ResolveJoinRequest(JoinDecision.ACCEPT)
            )

            assertEquals(Response.Status.OK.statusCode, response.status)
            val body = response.entity as ResolveJoinHostResponse
            assertEquals("123456", body.roomCode)
            assertEquals(RoomState.PENDING_2FA, body.state)
            assertEquals(expectedCorrectCode, body.twoFactorCorrectCode)

            verify(exactly = 1) {
                signalingService.resolveJoin(roomCode, hostSecret, JoinDecision.ACCEPT)
            }
            confirmVerified(signalingService)
        }

        @Test
        fun `should return CLOSED state when host rejects join request`() {
            val closedRoom = Room(roomCode, hostId, hostSecret, SecurityMode.STANDARD).also {
                it.requestJoin(guestId, guestSecret, sampleMetadata)
                it.resolveJoin(hostSecret, JoinDecision.REJECT)
            }
            every {
                signalingService.resolveJoin(roomCode, hostSecret, JoinDecision.REJECT)
            } returns closedRoom

            val response = controller.resolveJoin(
                rawCode = "123456",
                rawHostSecret = hostSecret.value,
                request = ResolveJoinRequest(JoinDecision.REJECT)
            )

            assertEquals(Response.Status.OK.statusCode, response.status)
            val body = response.entity as ResolveJoinHostResponse
            assertEquals(RoomState.CLOSED, body.state)
            assertNull(body.twoFactorCorrectCode)
        }

        @Test
        fun `should throw IllegalArgumentException when X-Client-Secret header is null or blank`() {
            assertThrows<IllegalArgumentException> {
                controller.resolveJoin("123456", null, ResolveJoinRequest(JoinDecision.ACCEPT))
            }
            assertThrows<IllegalArgumentException> {
                controller.resolveJoin("123456", "   ", ResolveJoinRequest(JoinDecision.ACCEPT))
            }

            verify(exactly = 0) {
                signalingService.resolveJoin(anyRoomCode(), anyClientSecret(), any())
            }
            confirmVerified(signalingService)
        }

        @Test
        fun `should throw IllegalArgumentException when X-Client-Secret has invalid length`() {
            assertThrows<IllegalArgumentException> {
                controller.resolveJoin("123456", "too-short-secret", ResolveJoinRequest(JoinDecision.ACCEPT))
            }

            verify(exactly = 0) {
                signalingService.resolveJoin(anyRoomCode(), anyClientSecret(), any())
            }
            confirmVerified(signalingService)
        }
    }

    @Nested
    inner class Submit2FATests {

        @Test
        fun `should return ACTIVE state when guest submits valid 2FA code`() {
            val room = Room(roomCode, hostId, hostSecret, SecurityMode.TWO_FACTOR).also {
                it.requestJoin(guestId, guestSecret, sampleMetadata)
                it.resolveJoin(hostSecret, JoinDecision.ACCEPT)
            }
            val correctCode = room.challenge2FA!!.correctCode
            room.submit2FA(guestId, guestSecret, correctCode)

            every {
                signalingService.submit2FA(roomCode, guestId, guestSecret, correctCode)
            } returns room

            val response = controller.submit2FA(
                rawCode = "123456",
                rawGuestId = guestId.value.toString(),
                rawGuestSecret = guestSecret.value,
                request = Submit2FARequest(correctCode.value)
            )

            assertEquals(Response.Status.OK.statusCode, response.status)
            val body = response.entity as RoomStateResponse
            assertEquals("123456", body.roomCode)
            assertEquals(RoomState.ACTIVE, body.state)

            verify(exactly = 1) {
                signalingService.submit2FA(roomCode, guestId, guestSecret, correctCode)
            }
            confirmVerified(signalingService)
        }

        @Test
        fun `should throw IllegalArgumentException when X-Client-Id header is missing or blank`() {
            assertThrows<IllegalArgumentException> {
                controller.submit2FA("123456", null, guestSecret.value, Submit2FARequest(123))
            }
            assertThrows<IllegalArgumentException> {
                controller.submit2FA("123456", "   ", guestSecret.value, Submit2FARequest(123))
            }

            verify(exactly = 0) {
                signalingService.submit2FA(anyRoomCode(), anyClientId(), anyClientSecret(), anyTwoFactorCode())
            }
            confirmVerified(signalingService)
        }

        @Test
        fun `should throw IllegalArgumentException when X-Client-Secret header is missing or blank`() {
            assertThrows<IllegalArgumentException> {
                controller.submit2FA("123456", guestId.value.toString(), null, Submit2FARequest(123))
            }
            assertThrows<IllegalArgumentException> {
                controller.submit2FA("123456", guestId.value.toString(), "  ", Submit2FARequest(123))
            }

            verify(exactly = 0) {
                signalingService.submit2FA(anyRoomCode(), anyClientId(), anyClientSecret(), anyTwoFactorCode())
            }
            confirmVerified(signalingService)
        }

        @Test
        fun `should throw IllegalArgumentException when X-Client-Id is not a valid UUID`() {
            assertThrows<IllegalArgumentException> {
                controller.submit2FA("123456", "invalid-uuid", guestSecret.value, Submit2FARequest(123))
            }

            verify(exactly = 0) {
                signalingService.submit2FA(anyRoomCode(), anyClientId(), anyClientSecret(), anyTwoFactorCode())
            }
            confirmVerified(signalingService)
        }

        @Test
        fun `should throw IllegalArgumentException when 2FA code is outside valid range 100 to 999`() {
            assertThrows<IllegalArgumentException> {
                controller.submit2FA("123456", guestId.value.toString(), guestSecret.value, Submit2FARequest(99))
            }
            assertThrows<IllegalArgumentException> {
                controller.submit2FA("123456", guestId.value.toString(), guestSecret.value, Submit2FARequest(1000))
            }

            verify(exactly = 0) {
                signalingService.submit2FA(anyRoomCode(), anyClientId(), anyClientSecret(), anyTwoFactorCode())
            }
            confirmVerified(signalingService)
        }
    }
}