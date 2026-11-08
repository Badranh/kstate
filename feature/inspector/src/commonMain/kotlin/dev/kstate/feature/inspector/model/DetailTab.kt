package dev.kstate.feature.inspector.model

enum class DetailTab(
    val title: String,
) {
    STATE("State"),
    EDIT("Edit"),
    EVENTS("Events"),

    /** Only in a shared session. */
    COMMENTS("Comments"),
}
