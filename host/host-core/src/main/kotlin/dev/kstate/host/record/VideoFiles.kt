package dev.kstate.host.record

import java.nio.file.Path

/**
 * One device's recorded video: [stream] holds packets with scrcpy's /video framing (config packets
 * included), [index] one entry a packet: u64 hostTs | u64 offset in [stream] | u8 flags.
 */
data class VideoFiles(
    val stream: Path,
    val index: Path,
) {
    companion object {
        const val FLAG_KEY_FRAME: Int = 1
        const val FLAG_CONFIG: Int = 2

        fun under(
            dir: Path,
            stem: String,
        ) = VideoFiles(dir.resolve("$stem.h264"), dir.resolve("$stem.idx"))
    }
}
