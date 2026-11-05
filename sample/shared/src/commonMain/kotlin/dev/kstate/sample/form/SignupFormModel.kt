package dev.kstate.sample.form

import dev.kstate.adapter.orbit.inspect
import kotlinx.coroutines.CoroutineScope
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.orbitContainer

data class SignupFormState(
    val name: String = "",
    val email: String = "",
    val errors: Set<SignupError> = emptySet(),
)

sealed interface SignupFormEffect {
    data class Submitted(
        val name: String,
    ) : SignupFormEffect
}

/** The Orbit MVI screen of the sample: a signup form with validation. */
class SignupFormModel(
    scope: CoroutineScope,
    private val validator: SignupValidator = SignupValidator(),
) : OrbitContainerHost<SignupFormState, SignupFormState, SignupFormEffect> {
    override val container = scope.orbitContainer<SignupFormState, SignupFormEffect>(SignupFormState())

    init {
        inspect("Signup")
    }

    fun onNameChanged(name: String) =
        intent {
            reduce { state.copy(name = name, errors = emptySet()) }
        }

    fun onEmailChanged(email: String) =
        intent {
            reduce { state.copy(email = email, errors = emptySet()) }
        }

    fun submit() =
        intent {
            val errors = validator.validate(state.name, state.email)
            if (errors.isEmpty()) {
                postSideEffect(SignupFormEffect.Submitted(name = state.name.trim()))
            } else {
                reduce { state.copy(errors = errors) }
            }
        }
}
