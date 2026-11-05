package dev.kstate.viewer.ui.timeline

enum class Direction { BACK, FORWARD }

/** What the viewer shows: the live session, or the moment it was paused or scrubbed to. */
sealed interface Playhead {
    data object Live : Playhead

    data class Paused(
        val at: Long,
    ) : Playhead

    fun timeAt(now: Long): Long =
        when (this) {
            Live -> now
            is Paused -> at
        }

    /** Space: pause where we are, or go back to live. */
    fun toggle(now: Long): Playhead = if (this == Live) Paused(now) else Live

    /** Left/Right: the previous or next state change; forward past the last one is live. */
    fun step(
        direction: Direction,
        changes: List<Long>,
    ): Playhead =
        when (direction) {
            Direction.BACK -> back(changes)
            Direction.FORWARD -> forward(changes)
        }

    private fun back(changes: List<Long>): Playhead =
        when (this) {
            Live -> changes.lastOrNull()?.let { Paused(it) } ?: Live
            is Paused -> Paused(changes.lastOrNull { it < at } ?: changes.firstOrNull() ?: at)
        }

    private fun forward(changes: List<Long>): Playhead =
        when (this) {
            Live -> Live
            is Paused -> changes.firstOrNull { it > at }?.let { Paused(it) } ?: Live
        }
}
