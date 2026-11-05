package dev.kstate.sdk.flow

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleLatestTest {
    @Test
    fun emitsTheFirstValueImmediately() =
        runTest {
            flow { emit(1) }.throttleLatest(100).test {
                assertEquals(1, awaitItem())
                assertEquals(0, currentTime)
                awaitComplete()
            }
        }

    @Test
    fun keepsOnlyTheLatestValueWithinAPeriodAndEmitsItAtTheEnd() =
        runTest {
            val upstream =
                flow {
                    emit(1)
                    delay(10)
                    emit(2)
                    delay(10)
                    emit(3)
                }

            upstream.throttleLatest(100).test {
                assertEquals(1, awaitItem())
                assertEquals(3, awaitItem())
                assertEquals(100, currentTime)
                awaitComplete()
            }
        }

    @Test
    fun valuesFurtherApartThanThePeriodAllPass() =
        runTest {
            val upstream =
                flow {
                    emit(1)
                    delay(150)
                    emit(2)
                    delay(150)
                    emit(3)
                }

            upstream.throttleLatest(100).test {
                assertEquals(listOf(1, 2, 3), listOf(awaitItem(), awaitItem(), awaitItem()))
                awaitComplete()
            }
        }
}
