package dev.kstate.protocol

/** Builds RFC 6901 JSON Pointers, the path format for redacted values and diff ops. */
public object JsonPointer {
    public const val ROOT: String = ""

    public fun String.child(key: String): String = "$this/${key.replace("~", "~0").replace("/", "~1")}"

    public fun String.child(index: Int): String = "$this/$index"
}
