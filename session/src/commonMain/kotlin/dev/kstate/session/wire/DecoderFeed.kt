package dev.kstate.session.wire

/** A platform video decoder fed Annex-B chunks. After it reports an error it is done, as WebCodecs' is. */
interface PacketDecoder {
    fun decode(
        data: ByteArray,
        hostTs: Long,
        keyFrame: Boolean,
    )

    fun close()
}

/**
 * Feeds a device's packets to a decoder made by [create] from the stream's config, and keeps the
 * picture coming: a decoder that fails is dropped and the next key frame gets a fresh one, instead
 * of the video staying frozen until the socket reconnects. Pictures always start at a key frame,
 * which carries the SPS/PPS in band. Not thread-safe: one caller feeds it.
 */
class DecoderFeed(
    private val create: (config: ByteArray, onError: (String) -> Unit) -> PacketDecoder,
) {
    private var config: ByteArray? = null
    private var decoder: PacketDecoder? = null

    /** The last decoder error, if any, until a new config comes. */
    var lastError: String? = null
        private set

    fun configure(config: ByteArray) {
        close()
        this.config = config
        lastError = null
    }

    fun packet(
        hostTs: Long,
        keyFrame: Boolean,
        data: ByteArray,
    ) {
        val config = config ?: return
        val current = decoder ?: (if (keyFrame) open(config) else return)
        current.decode(if (keyFrame) config + data else data, hostTs, keyFrame)
    }

    fun close() {
        decoder?.close()
        decoder = null
        config = null
    }

    private fun open(config: ByteArray): PacketDecoder {
        var made: PacketDecoder? = null
        // An error is about the decoder that raised it; one already replaced is ignored.
        made = create(config) { message -> if (made != null && decoder === made) fail(message) }
        decoder = made
        return made
    }

    private fun fail(message: String) {
        lastError = message
        decoder?.close()
        decoder = null
    }
}
