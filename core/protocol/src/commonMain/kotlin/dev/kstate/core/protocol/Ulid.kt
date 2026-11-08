package dev.kstate.core.protocol

import kotlin.random.Random

/** Universally unique, lexicographically sortable ids (https://github.com/ulid/spec). */
public object Ulid {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val TIME_CHARS = 10
    private const val RANDOM_CHARS = 16
    private const val BITS_PER_CHAR = 5
    private const val CHAR_MASK = 0x1F

    public fun generate(
        timeMillis: Long,
        random: Random = Random.Default,
    ): String =
        buildString(TIME_CHARS + RANDOM_CHARS) {
            for (index in TIME_CHARS - 1 downTo 0) {
                append(ALPHABET[((timeMillis ushr (index * BITS_PER_CHAR)) and CHAR_MASK.toLong()).toInt()])
            }
            repeat(RANDOM_CHARS) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
}
