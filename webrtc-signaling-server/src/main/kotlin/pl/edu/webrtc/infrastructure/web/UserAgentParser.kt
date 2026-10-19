package pl.edu.webrtc.infrastructure.web

import pl.edu.webrtc.domain.model.Browser
import pl.edu.webrtc.domain.model.DeviceMetadata
import pl.edu.webrtc.domain.model.DeviceType
import pl.edu.webrtc.domain.model.OperatingSystem

object UserAgentParser {

    fun parse(userAgent: String?): DeviceMetadata {
        val ua = userAgent?.trim().orEmpty()
        val os = detectOs(ua)
        val browser = detectBrowser(ua)
        val device = detectDevice(ua, os)

        return DeviceMetadata(
            deviceType = device,
            operatingSystem = os,
            browser = browser
        )
    }

    private fun detectOs(ua: String): OperatingSystem = when {
        ua.contains("Android", ignoreCase = true) -> OperatingSystem.ANDROID
        ua.contains("iPhone", ignoreCase = true) || ua.contains("iPad", ignoreCase = true) -> OperatingSystem.IOS
        ua.contains("Windows NT", ignoreCase = true) -> OperatingSystem.WINDOWS
        ua.contains("Mac OS X", ignoreCase = true) || ua.contains("Macintosh", ignoreCase = true) -> OperatingSystem.MACOS
        ua.contains("Linux", ignoreCase = true) -> OperatingSystem.LINUX
        else -> OperatingSystem.UNKNOWN
    }

    private fun detectBrowser(ua: String): Browser = when {
        ua.contains("Edg/", ignoreCase = true) -> Browser.EDGE
        ua.contains("OPR/", ignoreCase = true) || ua.contains("Opera", ignoreCase = true) -> Browser.OPERA
        ua.contains("Firefox/", ignoreCase = true) -> Browser.FIREFOX
        ua.contains("Chrome/", ignoreCase = true) && !ua.contains("Edg/", ignoreCase = true) -> Browser.CHROME
        ua.contains("Safari/", ignoreCase = true) && !ua.contains("Chrome/", ignoreCase = true) -> Browser.SAFARI
        else -> Browser.UNKNOWN
    }

    private fun detectDevice(ua: String, detectedOs: OperatingSystem): DeviceType = when {
        ua.contains("iPhone", ignoreCase = true) -> DeviceType.IPHONE
        ua.contains("iPad", ignoreCase = true) -> DeviceType.IPAD
        ua.contains("Android", ignoreCase = true) && ua.contains("Mobile", ignoreCase = true) -> DeviceType.ANDROID_PHONE
        ua.contains("Android", ignoreCase = true) -> DeviceType.ANDROID_TABLET
        ua.contains("Macintosh", ignoreCase = true) -> DeviceType.MAC
        detectedOs == OperatingSystem.WINDOWS || detectedOs == OperatingSystem.LINUX -> DeviceType.DESKTOP_PC
        else -> DeviceType.UNKNOWN
    }
}