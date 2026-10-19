package pl.edu.webrtc.domain.model

import java.security.SecureRandom

@JvmInline
value class ClientSecret(val value: String) {
    init {
        require(value.isNotBlank()) { "ClientSecret cannot be blank" }
        require(value.length == SECRET_LENGTH) { "ClientSecret length must be exactly $SECRET_LENGTH characters" }
    }

    companion object {
        private const val CHAR_POOL = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        const val SECRET_LENGTH = 32

        private val random = SecureRandom()

        fun generate(length: Int = SECRET_LENGTH): ClientSecret {
            val secret = CharArray(length){
                CHAR_POOL[random.nextInt(CHAR_POOL.length)]
            }.concatToString()

            return ClientSecret(secret)
        }

    }
}