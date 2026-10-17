package pl.edu.webrtc.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RoomTest {

    private val roomCode = RoomCode("123456")
    private val hostId = ClientId.generate()
    private val hostSecret = ClientSecret.generate()
    private val guestId = ClientId.generate()
    private val guestSecret = ClientSecret.generate()
    private val metadata = DeviceMetadata(DeviceType.DESKTOP_PC, OperatingSystem.WINDOWS, Browser.FIREFOX)

    private fun createRoom(mode: SecurityMode = SecurityMode.TWO_FACTOR): Room {
        return Room(roomCode, hostId, hostSecret, mode)
    }

    @Nested
    inner class InitializationAndJoinRequestTests {
        @Test
        fun `new room should be in WAITING_FOR_GUEST state with null guest data`() {
            val room = createRoom()
            assertEquals(RoomState.WAITING_FOR_GUEST, room.state)
            assertNull(room.guestId)
            assertNull(room.guestSecret)
            assertNull(room.guestMetadata)
            assertNull(room.challenge2FA)
        }

        @Test
        fun `requestJoin should transition state to PENDING_HOST_APPROVAL and save guest info`() {
            val room = createRoom()
            room.requestJoin(guestId, guestSecret, metadata)

            assertEquals(RoomState.PENDING_HOST_APPROVAL, room.state)
            assertEquals(guestId, room.guestId)
            assertEquals(guestSecret, room.guestSecret)
            assertEquals(metadata, room.guestMetadata)
        }

        @Test
        fun `requestJoin should throw IllegalStateException if room is not in WAITING_FOR_GUEST`() {
            val room = createRoom()
            room.requestJoin(guestId, guestSecret, metadata)

            assertThrows<IllegalStateException> {
                room.requestJoin(ClientId.generate(), ClientSecret.generate(), metadata)
            }
        }
    }

    @Nested
    inner class ResolveJoinTests {
        @Test
        fun `resolveJoin should throw IllegalArgumentException when secret does not match hostSecret`() {
            val room = createRoom()
            room.requestJoin(guestId, guestSecret, metadata)
            val wrongSecret = ClientSecret.generate()

            assertThrows<IllegalArgumentException> {
                room.resolveJoin(wrongSecret, JoinDecision.ACCEPT)
            }
        }

        @Test
        fun `resolveJoin should throw IllegalStateException when called before guest requests join`() {
            val room = createRoom()
            assertThrows<IllegalStateException> {
                room.resolveJoin(hostSecret, JoinDecision.ACCEPT)
            }
        }

        @Test
        fun `resolveJoin with REJECT should close the room immediately`() {
            val room = createRoom(SecurityMode.STANDARD)
            room.requestJoin(guestId, guestSecret, metadata)

            room.resolveJoin(hostSecret, JoinDecision.REJECT)

            assertEquals(RoomState.CLOSED, room.state)
            assertNull(room.challenge2FA)
        }

        @Test
        fun `resolveJoin with ACCEPT in STANDARD mode should transition directly to ACTIVE`() {
            val room = createRoom(SecurityMode.STANDARD)
            room.requestJoin(guestId, guestSecret, metadata)

            room.resolveJoin(hostSecret, JoinDecision.ACCEPT)

            assertEquals(RoomState.ACTIVE, room.state)
            assertNull(room.challenge2FA)
        }

        @Test
        fun `resolveJoin with ACCEPT in TWO_FACTOR mode should transition to PENDING_2FA and generate challenge`() {
            val room = createRoom(SecurityMode.TWO_FACTOR)
            room.requestJoin(guestId, guestSecret, metadata)

            room.resolveJoin(hostSecret, JoinDecision.ACCEPT)

            assertEquals(RoomState.PENDING_2FA, room.state)
            assertNotNull(room.challenge2FA)
            assertEquals(3, room.challenge2FA!!.options.size)
        }
    }

    @Nested
    inner class Submit2FATests {
        @Test
        fun `submit2FA should transition to ACTIVE when guest submits correct code`() {
            val room = createRoom(SecurityMode.TWO_FACTOR)
            room.requestJoin(guestId, guestSecret, metadata)
            room.resolveJoin(hostSecret, JoinDecision.ACCEPT)

            val correctCode = room.challenge2FA!!.correctCode
            room.submit2FA(guestId, guestSecret, correctCode)

            assertEquals(RoomState.ACTIVE, room.state)
            assertTrue(room.challenge2FA!!.isSuccessful())
        }

        @Test
        fun `submit2FA should permanently close room (Burn on Fail) when guest submits wrong code`() {
            val room = createRoom(SecurityMode.TWO_FACTOR)
            room.requestJoin(guestId, guestSecret, metadata)
            room.resolveJoin(hostSecret, JoinDecision.ACCEPT)

            val challenge = room.challenge2FA!!
            val wrongCode = challenge.options.first { it != challenge.correctCode }

            room.submit2FA(guestId, guestSecret, wrongCode)

            assertEquals(RoomState.CLOSED, room.state)
            assertFalse(room.challenge2FA!!.isSuccessful())

            assertThrows<IllegalStateException> {
                room.submit2FA(guestId, guestSecret, challenge.correctCode)
            }
        }

        @Test
        fun `submit2FA should throw IllegalArgumentException when called by host or with wrong guest credentials`() {
            val room = createRoom(SecurityMode.TWO_FACTOR)
            room.requestJoin(guestId, guestSecret, metadata)
            room.resolveJoin(hostSecret, JoinDecision.ACCEPT)
            val correctCode = room.challenge2FA!!.correctCode

            assertThrows<IllegalArgumentException> {
                room.submit2FA(hostId, hostSecret, correctCode)
            }
            assertThrows<IllegalArgumentException> {
                room.submit2FA(ClientId.generate(), guestSecret, correctCode)
            }
            assertThrows<IllegalArgumentException> {
                room.submit2FA(guestId, ClientSecret.generate(), correctCode)
            }
        }

        @Test
        fun `submit2FA should throw IllegalStateException when room is not in PENDING_2FA state`() {
            val room = createRoom(SecurityMode.STANDARD)
            room.requestJoin(guestId, guestSecret, metadata)
            room.resolveJoin(hostSecret, JoinDecision.ACCEPT)

            assertThrows<IllegalStateException> {
                room.submit2FA(guestId, guestSecret, TwoFactorCode(123))
            }
        }
    }

    @Nested
    inner class SignalingRoutingTests {
        @Test
        fun `canRouteSignaling should return false in all states except ACTIVE`() {
            val room = createRoom(SecurityMode.TWO_FACTOR)
            assertFalse(room.canRouteSignaling(hostId, hostSecret))

            room.requestJoin(guestId, guestSecret, metadata)
            assertFalse(room.canRouteSignaling(hostId, hostSecret))
            assertFalse(room.canRouteSignaling(guestId, guestSecret))

            room.resolveJoin(hostSecret, JoinDecision.ACCEPT)
            assertFalse(room.canRouteSignaling(hostId, hostSecret))
            assertFalse(room.canRouteSignaling(guestId, guestSecret))

            val wrongCode = room.challenge2FA!!.options.first { it != room.challenge2FA!!.correctCode }
            room.submit2FA(guestId, guestSecret, wrongCode)
            assertFalse(room.canRouteSignaling(hostId, hostSecret))
            assertFalse(room.canRouteSignaling(guestId, guestSecret))
        }

        @Test
        fun `canRouteSignaling should verify credentials properly when room is ACTIVE`() {
            val room = createRoom(SecurityMode.STANDARD)
            room.requestJoin(guestId, guestSecret, metadata)
            room.resolveJoin(hostSecret, JoinDecision.ACCEPT)

            assertTrue(room.canRouteSignaling(hostId, hostSecret))
            assertTrue(room.canRouteSignaling(guestId, guestSecret))

            assertFalse(room.canRouteSignaling(hostId, guestSecret))
            assertFalse(room.canRouteSignaling(guestId, hostSecret))
            assertFalse(room.canRouteSignaling(hostId, ClientSecret.generate()))
            assertFalse(room.canRouteSignaling(ClientId.generate(), hostSecret))
        }

        @Test
        fun `getRecipientId should route host to guest, guest to host, and unknown to null`() {
            val room = createRoom(SecurityMode.STANDARD)
            room.requestJoin(guestId, guestSecret, metadata)

            assertEquals(guestId, room.getRecipientId(hostId))
            assertEquals(hostId, room.getRecipientId(guestId))
            assertNull(room.getRecipientId(ClientId.generate()))
        }
    }
}