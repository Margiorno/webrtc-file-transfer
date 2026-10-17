package pl.edu.webrtc.infrastructure.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import pl.edu.webrtc.domain.model.Browser
import pl.edu.webrtc.domain.model.DeviceType
import pl.edu.webrtc.domain.model.OperatingSystem

class UserAgentParserTest {

    @Nested
    inner class DesktopCombinationsTests {

        @Test
        fun `should detect Windows, Firefox and Desktop PC`() {
            val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0"

            val metadata = UserAgentParser.parse(ua)

            assertEquals(DeviceType.DESKTOP_PC, metadata.deviceType)
            assertEquals(OperatingSystem.WINDOWS, metadata.operatingSystem)
            assertEquals(Browser.FIREFOX, metadata.browser)
        }

        @Test
        fun `should detect Windows, Chrome and Desktop PC`() {
            val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"

            val metadata = UserAgentParser.parse(ua)

            assertEquals(DeviceType.DESKTOP_PC, metadata.deviceType)
            assertEquals(OperatingSystem.WINDOWS, metadata.operatingSystem)
            assertEquals(Browser.CHROME, metadata.browser)
        }

        @Test
        fun `should detect Windows, Edge and Desktop PC even when Chrome and Safari tokens are present`() {
            val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.2792.79"

            val metadata = UserAgentParser.parse(ua)

            assertEquals(DeviceType.DESKTOP_PC, metadata.deviceType)
            assertEquals(OperatingSystem.WINDOWS, metadata.operatingSystem)
            assertEquals(Browser.EDGE, metadata.browser)
        }

        @Test
        fun `should detect Windows, Opera (OPR token) and Desktop PC`() {
            val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 OPR/114.0.0.0"

            val metadata = UserAgentParser.parse(ua)

            assertEquals(DeviceType.DESKTOP_PC, metadata.deviceType)
            assertEquals(OperatingSystem.WINDOWS, metadata.operatingSystem)
            assertEquals(Browser.OPERA, metadata.browser)
        }

        @Test
        fun `should detect legacy Opera token`() {
            val ua = "Opera/9.80 (X11; Linux x86_64) Presto/2.12.388 Version/12.16"

            val metadata = UserAgentParser.parse(ua)

            assertEquals(DeviceType.DESKTOP_PC, metadata.deviceType)
            assertEquals(OperatingSystem.LINUX, metadata.operatingSystem)
            assertEquals(Browser.OPERA, metadata.browser)
        }

        @Test
        fun `should detect macOS, Safari and Mac`() {
            val ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"

            val metadata = UserAgentParser.parse(ua)

            assertEquals(DeviceType.MAC, metadata.deviceType)
            assertEquals(OperatingSystem.MACOS, metadata.operatingSystem)
            assertEquals(Browser.SAFARI, metadata.browser)
        }

        @Test
        fun `should detect Linux, Firefox and Desktop PC`() {
            val ua = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0"

            val metadata = UserAgentParser.parse(ua)

            assertEquals(DeviceType.DESKTOP_PC, metadata.deviceType)
            assertEquals(OperatingSystem.LINUX, metadata.operatingSystem)
            assertEquals(Browser.FIREFOX, metadata.browser)
        }
    }

    @Nested
    inner class MobileAndTabletCombinationsTests {

        @Test
        fun `should detect iOS, Safari and iPhone despite Like Mac OS X token`() {
            val ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1"

            val metadata = UserAgentParser.parse(ua)

            assertEquals(DeviceType.IPHONE, metadata.deviceType)
            assertEquals(OperatingSystem.IOS, metadata.operatingSystem)
            assertEquals(Browser.SAFARI, metadata.browser)
        }

        @Test
        fun `should detect iOS, Safari and iPad`() {
            val ua = "Mozilla/5.0 (iPad; CPU OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1"

            val metadata = UserAgentParser.parse(ua)

            assertEquals(DeviceType.IPAD, metadata.deviceType)
            assertEquals(OperatingSystem.IOS, metadata.operatingSystem)
            assertEquals(Browser.SAFARI, metadata.browser)
        }

        @Test
        fun `should detect Android, Chrome and Android Phone when Mobile token is present`() {
            val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.6668.70 Mobile Safari/537.36"

            val metadata = UserAgentParser.parse(ua)

            assertEquals(DeviceType.ANDROID_PHONE, metadata.deviceType)
            assertEquals(OperatingSystem.ANDROID, metadata.operatingSystem)
            assertEquals(Browser.CHROME, metadata.browser)
        }

        @Test
        fun `should detect Android, Chrome and Android Tablet when Mobile token is absent`() {
            val ua = "Mozilla/5.0 (Linux; Android 14; SM-X910) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.6668.70 Safari/537.36"

            val metadata = UserAgentParser.parse(ua)

            assertEquals(DeviceType.ANDROID_TABLET, metadata.deviceType)
            assertEquals(OperatingSystem.ANDROID, metadata.operatingSystem)
            assertEquals(Browser.CHROME, metadata.browser)
        }
    }

    @Nested
    inner class EdgeCasesAndFallbackTests {

        @Test
        fun `should return UNKNOWN for all fields when userAgent is null`() {
            val metadata = UserAgentParser.parse(null)

            assertEquals(DeviceType.UNKNOWN, metadata.deviceType)
            assertEquals(OperatingSystem.UNKNOWN, metadata.operatingSystem)
            assertEquals(Browser.UNKNOWN, metadata.browser)
        }

        @Test
        fun `should return UNKNOWN for all fields when userAgent is blank or whitespace`() {
            val metadata = UserAgentParser.parse("   \t\n  ")

            assertEquals(DeviceType.UNKNOWN, metadata.deviceType)
            assertEquals(OperatingSystem.UNKNOWN, metadata.operatingSystem)
            assertEquals(Browser.UNKNOWN, metadata.browser)
        }

        @Test
        fun `should return UNKNOWN for unrecognized CLI or bot userAgent`() {
            val metadata = UserAgentParser.parse("curl/8.7.1")

            assertEquals(DeviceType.UNKNOWN, metadata.deviceType)
            assertEquals(OperatingSystem.UNKNOWN, metadata.operatingSystem)
            assertEquals(Browser.UNKNOWN, metadata.browser)
        }

        @Test
        fun `should parse case-insensitively`() {
            val ua = "mozilla/5.0 (windows nt 10.0) firefox/130.0"

            val metadata = UserAgentParser.parse(ua)

            assertEquals(DeviceType.DESKTOP_PC, metadata.deviceType)
            assertEquals(OperatingSystem.WINDOWS, metadata.operatingSystem)
            assertEquals(Browser.FIREFOX, metadata.browser)
        }
    }
}