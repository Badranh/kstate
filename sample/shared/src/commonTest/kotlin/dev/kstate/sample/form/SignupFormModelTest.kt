package dev.kstate.sample.form

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SignupFormModelTest {
    @Test
    fun typingUpdatesTheFields() =
        runTest {
            val model = SignupFormModel(backgroundScope)

            model.container.stateFlow.test {
                assertEquals(SignupFormState(), awaitItem())
                model.onNameChanged("Ada")
                assertEquals("Ada", awaitItem().name)
                model.onEmailChanged("ada@example.com")
                assertEquals(SignupFormState(name = "Ada", email = "ada@example.com"), awaitItem())
            }
        }

    /**
     * A text field shows the state, so a keystroke must be in it by the time onValueChange returns:
     * one applied later lets the next keystroke start from the old text and lose characters.
     */
    @Test
    fun aKeystrokeIsInTheStateBeforeTheNextOne() =
        runTest {
            val model = SignupFormModel(backgroundScope)

            model.onNameChanged("A")
            assertEquals("A", model.container.stateFlow.value.name)
            model.onEmailChanged("a@")
            assertEquals("a@", model.container.stateFlow.value.email)
        }

    @Test
    fun submittingInvalidInputShowsErrorsAndSendsNothing() =
        runTest {
            val model = SignupFormModel(backgroundScope)

            model.container.stateFlow.test {
                skipItems(1)
                model.submit()
                assertEquals(setOf(SignupError.NameMissing, SignupError.EmailInvalid), awaitItem().errors)
            }
            model.container.sideEffectFlow.test { expectNoEvents() }
        }

    @Test
    fun submittingValidInputEmitsSubmitted() =
        runTest {
            val model = SignupFormModel(backgroundScope)

            model.container.sideEffectFlow.test {
                model.onNameChanged("Ada")
                model.onEmailChanged("ada@example.com")
                model.submit()
                assertEquals(SignupFormEffect.Submitted(name = "Ada"), awaitItem())
            }
        }

    @Test
    fun editingAFieldClearsPreviousErrors() =
        runTest {
            val model = SignupFormModel(backgroundScope)

            model.container.stateFlow.test {
                skipItems(1)
                model.submit()
                skipItems(1)
                model.onNameChanged("A")
                assertEquals(emptySet(), awaitItem().errors)
            }
        }
}
