package pl.edu.webrtc.domain.model

import java.security.SecureRandom
import kotlin.random.asKotlinRandom

@JvmInline
value class TwoFactorCode(val value: Int) {
    init {
        require(value in VALID_RANGE) {
            "Two-factor code must be in range $VALID_RANGE, got: $value"
        }
    }

    companion object {
        private val VALID_RANGE = 100..999
        private val secureRandom = SecureRandom().asKotlinRandom()

        fun generate(): TwoFactorCode {
            return TwoFactorCode(VALID_RANGE.random(secureRandom))
        }
    }
}