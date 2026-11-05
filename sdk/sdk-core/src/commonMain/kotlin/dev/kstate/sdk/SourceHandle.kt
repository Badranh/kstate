package dev.kstate.sdk

/** A tracked state source. Closing it stops tracking and clears it on the host. */
public interface SourceHandle {
    public val sourceId: String

    public fun close()

    public companion object {
        /** Returned while Kstate is not started. */
        public val None: SourceHandle =
            object : SourceHandle {
                override val sourceId: String = ""

                override fun close() = Unit
            }
    }
}
