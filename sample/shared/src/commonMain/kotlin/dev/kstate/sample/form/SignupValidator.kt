package dev.kstate.sample.form

enum class SignupError { NameMissing, EmailInvalid }

class SignupValidator {
    fun validate(
        name: String,
        email: String,
    ): Set<SignupError> =
        buildSet {
            if (name.isBlank()) add(SignupError.NameMissing)
            if (!email.isWellFormedEmail()) add(SignupError.EmailInvalid)
        }

    private fun String.isWellFormedEmail(): Boolean {
        val parts = trim().split('@')
        return parts.size == 2 && parts[0].isNotEmpty() && parts[1].contains('.')
    }
}
