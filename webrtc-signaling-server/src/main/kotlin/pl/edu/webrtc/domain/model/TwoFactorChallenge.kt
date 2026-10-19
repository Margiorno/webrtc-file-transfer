package pl.edu.webrtc.domain.model

import kotlin.random.asKotlinRandom

data class TwoFactorChallenge(
    val options: List<TwoFactorCode>,
    val correctCode: TwoFactorCode,
    val guestAnswer: TwoFactorCode? = null
) {
    init {
        require(options.size == OPTIONS_COUNT) { "Challenge must have exactly $OPTIONS_COUNT options" }
        require(options.distinct().size == OPTIONS_COUNT) { "Options must be unique" }
        require(options.contains(correctCode)) { "Options must contain the correct code" }
    }

    fun withGuestAnswer(answer: TwoFactorCode) = copy(guestAnswer = answer)

    fun isCompleted(): Boolean = guestAnswer != null

    fun isSuccessful(): Boolean = isCompleted() && guestAnswer == correctCode

    companion object {
        const val OPTIONS_COUNT = 3

        private val secureRandom = java.security.SecureRandom().asKotlinRandom()

        fun generate(): TwoFactorChallenge {
            val generated = generateSequence { TwoFactorCode.generate() }
                .distinctBy { it.value }
                .take(OPTIONS_COUNT)
                .toList()

            return TwoFactorChallenge(
                options = generated.shuffled(secureRandom),
                correctCode = generated.random(secureRandom)
            )
        }
    }
}