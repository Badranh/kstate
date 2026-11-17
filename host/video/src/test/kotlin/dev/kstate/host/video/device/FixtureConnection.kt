package dev.kstate.host.video.device

import dev.kstate.host.video.scrcpy.VideoConnection
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** A stream recorded from the emulator's scrcpy server, played back as a device's connection. */
internal class FixtureConnection(
    fixture: String = UPRIGHT,
) : VideoConnection {
    // The first byte is the dummy the launcher reads to know the server is up.
    override val video: InputStream =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$fixture")) { "missing fixture $fixture" }
            .apply { read() }
    override val control = ByteArrayOutputStream()
    var closed = false

    override fun close() {
        closed = true
    }

    companion object {
        /** 64 pictures of an upright device, 142x320. */
        const val UPRIGHT = "scrcpy-v5.0.1-h264-142x320.bin"

        /** 12 pictures upright (142x320), then the device is turned on its side: 14 more at 320x142. */
        const val TURNED = "scrcpy-v5.0.1-h264-rotation.bin"
    }
}
