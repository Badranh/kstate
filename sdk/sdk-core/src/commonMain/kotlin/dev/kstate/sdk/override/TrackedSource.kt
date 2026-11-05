package dev.kstate.sdk.override

import dev.kstate.protocol.StateSnapshot
import dev.kstate.sdk.encoding.EncodedState
import dev.kstate.sdk.encoding.RawStrategy
import dev.kstate.sdk.encoding.StateEncoder
import dev.kstate.sdk.redaction.Redactor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge

/** An override replacing what the UI shows. */
internal class Override<T>(
    val value: T,
)

/** A value seen on a source: from the app ([real]) or an override, and whether the UI hides it. */
internal class Observed<T>(
    val value: T,
    val suppressed: Boolean,
    val real: Boolean,
)

internal class Recorded<T>(
    val seq: Long,
    val observed: Observed<T>,
)

/**
 * What the SDK keeps for one source: sequence numbers, the ring buffer, the optional override
 * (inspectable() sources), decoding and write-through. Confined to the SDK dispatcher; the override
 * is a thread-safe state flow because the UI reads it. Commands run through [commands].
 */
internal class TrackedSource<T>(
    val sourceId: String,
    private val encoder: StateEncoder<T>,
    val decoder: StateDecoder<T>?,
    private val writeThrough: ((T) -> Unit)?,
    private val redactor: Redactor,
    private val override: MutableStateFlow<Override<T>?>?,
    ringSize: Int,
    ringBytes: Long,
) {
    private val ring = RingBuffer<T>(ringSize, ringBytes)
    private var nextSeq = 0L
    private var latestReal: Observed<T>? = null
    private var sizeEstimate = INITIAL_SIZE_ESTIMATE

    val commands = SourceCommands(this)
    val canOverlay: Boolean get() = override != null
    val canWriteThrough: Boolean get() = writeThrough != null
    val canDecode: Boolean get() = decoder != null
    val overrideActive: Boolean get() = override?.value != null

    /** Real values, flagged suppressed while an override shows, plus each override change. */
    fun observed(states: Flow<T>): Flow<Observed<T>> {
        val overrides = override ?: return states.map { Observed(it, suppressed = false, real = true) }
        val real = states.map { Observed(it, suppressed = overrides.value != null, real = true) }
        val switches =
            overrides.drop(1).mapNotNull { current ->
                current?.let { Observed(it.value, suppressed = false, real = false) }
                    ?: latestReal?.let { Observed(it.value, suppressed = false, real = true) }
            }
        return merge(real, switches)
    }

    /** Every conflated value gets a sequence number and a ring slot, even if the throttle never sends it. */
    fun record(observed: Observed<T>): Recorded<T> {
        val seq = nextSeq++
        ring.put(seq, observed.value, sizeEstimate)
        if (observed.real) latestReal = observed
        return Recorded(seq, observed)
    }

    fun snapshot(recorded: Recorded<T>): StateSnapshot {
        val encoded = encode(recorded.observed.value)
        val redacted = redactor.redact(encoded.json, encoded.sensitivePaths)
        sizeEstimate =
            redacted.json
                .toString()
                .length
                .toLong()
        return StateSnapshot(
            sourceId = sourceId,
            seq = recorded.seq,
            state = redacted.json,
            encoding = encoded.encoding,
            suppressed = recorded.observed.suppressed,
            redacted = redacted.paths,
            partial = encoded.partial,
        )
    }

    fun valueAt(seq: Long): T? = ring[seq]

    /** What the UI shows now: the override if there is one, the latest real value otherwise. */
    fun currentValue(): T? = override?.value?.value ?: latestReal?.value

    /** False when this source has no override (not created with inspectable()). */
    fun show(value: T?): Boolean {
        val overrides = override ?: return false
        overrides.value = value?.let(::Override)
        return true
    }

    fun latestRealValue(): T? = latestReal?.value

    /** False when the app gave no write-through callback. */
    fun writeIntoApp(value: T): Boolean {
        val write = writeThrough ?: return false
        write(value)
        override?.value = null
        return true
    }

    fun encode(value: T): EncodedState =
        try {
            encoder.encode(value)
        } catch (_: Exception) {
            RawStrategy.encode(value)
        }

    fun redactedPaths(encoded: EncodedState): List<String> = redactor.redact(encoded.json, encoded.sensitivePaths).paths

    private companion object {
        const val INITIAL_SIZE_ESTIMATE = 256L
    }
}
