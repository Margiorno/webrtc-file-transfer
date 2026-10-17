package pl.edu.webrtc.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import pl.edu.webrtc.infrastructure.web.UserAgentParser
import java.util.UUID

class DomainModelsTest {

    @Nested
    inner class ClientIdTests {
        @Test
        fun `should generate valid ClientId`() {
            val id1 = ClientId.generate()
            val id2 = ClientId.generate()
            assertNotEquals(id1, id2)
        }

        @Test
        fun `should parse ClientId from valid UUID string`() {
            val rawUuid = UUID.randomUUID().toString()
            val clientId = ClientId.fromString(rawUuid)
            assertEquals(rawUuid, clientId.value.toString())
        }

        @Test
        fun `should throw exception when parsing invalid UUID string`() {
            assertThrows<IllegalArgumentException> {
                ClientId.fromString("not-a-valid-uuid")
            }
        }
    }

    @Nested
    inner class ClientSecretTests {
        @Test
        fun `should generate valid 32-character ClientSecret`() {
            val secret = ClientSecret.generate()
            assertEquals(ClientSecret.SECRET_LENGTH, secret.value.length)
            assertTrue(secret.value.isNotBlank())
        }

        @Test
        fun `should accept valid 32-character string`() {
            val raw = "a".repeat(32)
            val secret = ClientSecret(raw)
            assertEquals(raw, secret.value)
        }

        @Test
        fun `should throw exception when secret is blank or whitespace`() {
            assertThrows<IllegalArgumentException> { ClientSecret("") }
            assertThrows<IllegalArgumentException> { ClientSecret(" ".repeat(32)) }
        }

        @Test
        fun `should throw exception when secret length is not 32`() {
            assertThrows<IllegalArgumentException> { ClientSecret("a".repeat(31)) }
            assertThrows<IllegalArgumentException> { ClientSecret("a".repeat(33)) }
        }
    }

    @Nested
    inner class RoomCodeTests {
        @Test
        fun `should generate valid 6-digit RoomCode`() {
            val code = RoomCode.generate()
            assertEquals(6, code.value.length)
            assertTrue(code.value.all { it.isDigit() })
        }

        @Test
        fun `should accept valid 6-digit string including leading zeros`() {
            val code = RoomCode("001234")
            assertEquals("001234", code.value)
        }

        @Test
        fun `should throw exception when RoomCode is shorter or longer than 6 digits`() {
            assertThrows<IllegalArgumentException> { RoomCode("12345") }
            assertThrows<IllegalArgumentException> { RoomCode("1234567") }
            assertThrows<IllegalArgumentException> { RoomCode("") }
        }

        @Test
        fun `should throw exception when RoomCode contains letters or special characters`() {
            assertThrows<IllegalArgumentException> { RoomCode("12345a") }
            assertThrows<IllegalArgumentException> { RoomCode("12-456") }
            assertThrows<IllegalArgumentException> { RoomCode("123 56") }
        }
    }

    @Nested
    inner class TwoFactorCodeTests {
        @Test
        fun `should generate code within valid range 100 to 999`() {
            repeat(100) {
                val code = TwoFactorCode.generate()
                assertTrue(code.value in 100..999)
            }
        }

        @Test
        fun `should accept boundary values 100 and 999`() {
            assertEquals(100, TwoFactorCode(100).value)
            assertEquals(999, TwoFactorCode(999).value)
        }

        @Test
        fun `should throw exception for values outside 100 to 999`() {
            assertThrows<IllegalArgumentException> { TwoFactorCode(99) }
            assertThrows<IllegalArgumentException> { TwoFactorCode(0) }
            assertThrows<IllegalArgumentException> { TwoFactorCode(-150) }
            assertThrows<IllegalArgumentException> { TwoFactorCode(1000) }
        }
    }

    @Nested
    inner class DeviceMetadataTests {
        @Test
        fun `should create valid DeviceMetadata with enums`() {
            val metadata = DeviceMetadata(DeviceType.DESKTOP_PC, OperatingSystem.WINDOWS, Browser.FIREFOX)
            assertEquals(DeviceType.DESKTOP_PC, metadata.deviceType)
            assertEquals(OperatingSystem.WINDOWS, metadata.operatingSystem)
            assertEquals(Browser.FIREFOX, metadata.browser)
        }

        @Test
        fun `UserAgentParser should return UNKNOWN fields for blank or invalid user agent`() {
            val metadata = UserAgentParser.parse("")

            assertEquals(DeviceType.UNKNOWN, metadata.deviceType)
            assertEquals(OperatingSystem.UNKNOWN, metadata.operatingSystem)
            assertEquals(Browser.UNKNOWN, metadata.browser)
        }
    }

    @Nested
    inner class TwoFactorChallengeTests {
        private val code1 = TwoFactorCode(111)
        private val code2 = TwoFactorCode(222)
        private val code3 = TwoFactorCode(333)
        private val code4 = TwoFactorCode(444)

        @Test
        fun `should generate valid challenge with 3 unique options and included correctCode`() {
            val challenge = TwoFactorChallenge.generate()
            assertEquals(3, challenge.options.size)
            assertEquals(3, challenge.options.distinct().size)
            assertTrue(challenge.options.contains(challenge.correctCode))
            assertNull(challenge.guestAnswer)
            assertFalse(challenge.isCompleted())
            assertFalse(challenge.isSuccessful())
        }

        @Test
        fun `should throw exception when options size is not exactly 3`() {
            assertThrows<IllegalArgumentException> {
                TwoFactorChallenge(options = listOf(code1, code2), correctCode = code1)
            }
            assertThrows<IllegalArgumentException> {
                TwoFactorChallenge(options = listOf(code1, code2, code3, code4), correctCode = code1)
            }
        }

        @Test
        fun `should throw exception when options contain duplicates`() {
            assertThrows<IllegalArgumentException> {
                TwoFactorChallenge(options = listOf(code1, code1, code2), correctCode = code1)
            }
        }

        @Test
        fun `should throw exception when correctCode is not in options`() {
            assertThrows<IllegalArgumentException> {
                TwoFactorChallenge(options = listOf(code1, code2, code3), correctCode = code4)
            }
        }

        @Test
        fun `should mark challenge as completed and successful when guest provides correct answer`() {
            val challenge = TwoFactorChallenge(options = listOf(code1, code2, code3), correctCode = code2)
            val answered = challenge.withGuestAnswer(code2)

            assertTrue(answered.isCompleted())
            assertTrue(answered.isSuccessful())
            assertEquals(code2, answered.guestAnswer)
            assertNull(challenge.guestAnswer)
        }

        @Test
        fun `should mark challenge as completed but unsuccessful when guest provides wrong answer`() {
            val challenge = TwoFactorChallenge(options = listOf(code1, code2, code3), correctCode = code2)
            val answered = challenge.withGuestAnswer(code1)

            assertTrue(answered.isCompleted())
            assertFalse(answered.isSuccessful())
        }
    }
}