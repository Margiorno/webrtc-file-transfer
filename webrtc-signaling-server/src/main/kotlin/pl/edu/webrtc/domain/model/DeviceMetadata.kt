package pl.edu.webrtc.domain.model

enum class DeviceType {
    DESKTOP_PC,
    MAC,
    IPHONE,
    IPAD,
    ANDROID_PHONE,
    ANDROID_TABLET,
    UNKNOWN
}

enum class OperatingSystem {
    WINDOWS,
    MACOS,
    LINUX,
    ANDROID,
    IOS,
    UNKNOWN
}

enum class Browser {
    CHROME,
    FIREFOX,
    SAFARI,
    EDGE,
    OPERA,
    UNKNOWN
}

data class DeviceMetadata(
    val deviceType: DeviceType,
    val operatingSystem: OperatingSystem,
    val browser: Browser
)