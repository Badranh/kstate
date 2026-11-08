package dev.kstate.core.session.data.video

/** The WebCodecs codec string for an H.264 stream, from its SPS: `avc1.PPCCLL`. */
object AvcCodec {
    private const val SPS = 7
    private const val NAL_TYPE_MASK = 0x1F
    private const val BYTE_MASK = 0xFF
    private const val HEX = 16

    /** profile_idc, the constraint flags and level_idc follow the one-byte NAL header. */
    private val PROFILE_LEVEL = 1..3
    private const val START_CODE = 3
    private const val FALLBACK = "avc1.42E01F"

    fun fromConfig(annexB: ByteArray): String {
        val sps = nalUnits(annexB).firstOrNull { it.isNotEmpty() && (it[0].toInt() and NAL_TYPE_MASK) == SPS }
        if (sps == null || sps.size <= PROFILE_LEVEL.last) return FALLBACK
        return "avc1." +
            PROFILE_LEVEL.joinToString(
                "",
            ) { (sps[it].toInt() and BYTE_MASK).toString(HEX).padStart(2, '0').uppercase() }
    }

    /** NAL units without their 3- or 4-byte start codes. */
    private fun nalUnits(bytes: ByteArray): List<ByteArray> {
        val starts =
            (0 until bytes.size - 2).filter {
                bytes[it].toInt() == 0 && bytes[it + 1].toInt() == 0 &&
                    bytes[it + 2].toInt() == 1
            }
        return starts.mapIndexed { index, start ->
            val end =
                starts.getOrNull(index + 1)?.let { next ->
                    if (next > 0 &&
                        bytes[next - 1].toInt() == 0
                    ) {
                        next - 1
                    } else {
                        next
                    }
                }
                    ?: bytes.size
            bytes.copyOfRange(start + START_CODE, end)
        }
    }
}
