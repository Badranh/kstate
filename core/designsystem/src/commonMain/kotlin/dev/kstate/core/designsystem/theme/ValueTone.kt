package dev.kstate.core.designsystem.theme

/** What a value in the state tree is, so it can be coloured like code. */
enum class ValueTone {
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
    REDACTED,
    CONTAINER,
}
