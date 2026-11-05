package dev.kstate.viewer.client

/** The scrubbable range and what happened in it, all in host time. */
data class TimelineView(
    val start: Long,
    val end: Long,
    val lanes: List<TimelineLane>,
    val eventTimes: List<Long>,
)

/** One source's lane: a tick for every state its UI showed, and red marks for suppressed ones. */
data class TimelineLane(
    val key: String,
    val name: String,
    val ticks: List<Long>,
    /** Real emissions the app hid behind an override. */
    val suppressedTicks: List<Long> = emptyList(),
)
