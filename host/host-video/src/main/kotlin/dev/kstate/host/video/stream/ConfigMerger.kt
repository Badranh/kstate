package dev.kstate.host.video.stream

/** Holds a config packet (SPS/PPS) and prepends it to the next media packet, as decoders expect. */
class ConfigMerger {
    private var pending: ByteArray? = null

    fun merge(packet: StreamEvent.Packet): StreamEvent.Packet? =
        if (packet.config) {
            pending = packet.data
            null
        } else {
            withPendingConfig(packet)
        }

    private fun withPendingConfig(packet: StreamEvent.Packet): StreamEvent.Packet {
        val config = pending ?: return packet
        pending = null
        return StreamEvent.Packet(
            config = false,
            keyFrame = packet.keyFrame,
            ptsMicros = packet.ptsMicros,
            data =
                config + packet.data,
        )
    }
}
