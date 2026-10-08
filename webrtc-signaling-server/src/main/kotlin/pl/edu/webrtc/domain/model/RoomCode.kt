package pl.edu.webrtc.domain.model

import java.security.SecureRandom

@JvmInline
value class RoomCode(val value: String) {
    init {
        require(value.matches(REGEX)) { "Room code must be exactly $CODE_LENGTH digits, got: '$value'" }
    }

    companion object {
        const val CODE_LENGTH = 6
        private val REGEX = Regex("^\\d{$CODE_LENGTH}}")
        private val random = SecureRandom()

        fun generate(length: Int = CODE_LENGTH): RoomCode {
            val code = CharArray(CODE_LENGTH) {
                random.nextInt(10).digitToChar()
            }.concatToString()

            return RoomCode(code)
        }
    }
}