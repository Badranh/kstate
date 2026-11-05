package dev.kstate.viewer.ui.timeline

enum class Direction { BACK, FORWARD }

/** What the viewer shows: the live session, the moment it was paused or scrubbed to, or a recording playing. */
sealed interface Playhead {
    data object Live : Playhead

    data class Paused(
        val at: Long,
    ) : Playhead

    /** A recording playing on its own: [at] moves with the clock. */
    data class Playing(
        val at: Long,
    ) : Playhead

    /** The moment shown; null while following the live session. */
    val shownAt: Long?
        get() =
            when (this) {
                Live -> null
                is Paused -> at
                is Playing -> at
            }

    fun timeAt(now: Long): Long = shownAt ?: now

    /** Space in a live session: pause where we are, or go back to live. */
    fun toggle(now: Long): Playhead = if (this == Live) Paused(now) else Live

    /** Space in a recording: play from here (from the start once at the end), or pause where it got. */
    fun togglePlay(
        start: Long,
        end: Long,
    ): Playhead =
        when (this) {
            is Playing -> Paused(at)
            is Paused -> Playing(if (at >= end) start else at)
            Live -> Playing(start)
        }

    /** A playing recording moves on by [elapsed] and pauses at [end]; anything else stays. */
    fun advance(
        elapsed: Long,
        end: Long,
    ): Playhead =
        when (this) {
            is Playing -> (at + elapsed).let { if (it >= end) Paused(end) else Playing(it) }
            else -> this
        }

    /** Left/Right: the previous or next state change; forward past the last one is live. */
    fun step(
        direction: Direction,
        changes: List<Long>,
    ): Playhead {
        val from = if (this is Playing) Paused(at) else this
        return when (direction) {
            Direction.BACK -> from.back(changes)
            Direction.FORWARD -> from.forward(changes)
        }
    }

    private fun back(changes: List<Long>): Playhead =
        when (this) {
            is Paused -> Paused(changes.lastOrNull { it < at } ?: changes.firstOrNull() ?: at)
            else -> changes.lastOrNull()?.let { Paused(it) } ?: Live
        }

    private fun forward(changes: List<Long>): Playhead =
        when (this) {
            is Paused -> changes.firstOrNull { it > at }?.let { Paused(it) } ?: Live
            else -> Live
        }
}
