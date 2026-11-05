package dev.kstate.host.timeline

/** Where seeking finds video: the live window in memory, or a recording on disk. */
interface GopSource {
    /** Host time of the first picture, null when there is none. */
    val earliest: Long?

    val latest: Long?

    /** What a fresh decoder needs to show [hostTs]; null before the first key frame. */
    fun gopAt(hostTs: Long): Gop?

    fun frameTimes(): List<Long>
}
