package dev.kstate.sample.form

import kotlin.test.Test
import kotlin.test.assertEquals

class SignupValidatorTest {
    private val validator = SignupValidator()

    @Test
    fun acceptsANameAndAWellFormedEmail() {
        assertEquals(emptySet(), validator.validate(name = "Ada", email = "ada@example.com"))
    }

    @Test
    fun rejectsABlankName() {
        assertEquals(setOf(SignupError.NameMissing), validator.validate(name = "  ", email = "ada@example.com"))
    }

    @Test
    fun rejectsAnEmailWithoutAnAtSign() {
        assertEquals(setOf(SignupError.EmailInvalid), validator.validate(name = "Ada", email = "ada.example.com"))
    }

    @Test
    fun rejectsAnEmailWithoutADomainDot() {
        assertEquals(setOf(SignupError.EmailInvalid), validator.validate(name = "Ada", email = "ada@example"))
    }

    @Test
    fun reportsEveryProblemAtOnce() {
        assertEquals(
            setOf(SignupError.NameMissing, SignupError.EmailInvalid),
            validator.validate(name = "", email = ""),
        )
    }
}
