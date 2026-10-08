package pl.edu.webrtc.domain.model

data class DeviceMetadata(
    val deviceName: String,
    val operatingSystem: String,
    val browser: String
) {
    init {
        require(deviceName.isNotBlank()) {"deviceName must not be blank"}
        require(operatingSystem.isNotBlank()) { "operatingSystem must not be blank" }
        require(browser.isNotBlank()) {"browser must not be blank"}
    }

}