package pl.edu.webrtc.domain.model

import java.util.UUID

@JvmInline
value class ClientId(val value: UUID) {
    companion object{
        fun generate(): ClientId = ClientId(UUID.randomUUID())
        fun fromString(raw: String): ClientId = ClientId(UUID.fromString(raw))
    }
}