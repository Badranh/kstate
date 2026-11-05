package dev.kstate.viewer.ui.timeline

/** Maps host times onto a timeline of [width] pixels and back. */
class TimelineScale(
    private val start: Long,
    private val end: Long,
    private val width: Float,
) {
    private val span = (end - start).coerceAtLeast(1)

    fun x(time: Long): Float = if (end == start) width else (time - start) * width / span

    fun time(x: Float): Long = (start + (x.coerceIn(0f, width) / width * span).toLong()).coerceIn(start, end)
}
