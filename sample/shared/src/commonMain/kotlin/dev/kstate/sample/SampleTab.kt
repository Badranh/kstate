package dev.kstate.sample

/** One tab per architecture the SDK must support. */
enum class SampleTab(
    val label: String,
    val glyph: String,
) {
    Counter("MVVM", "+"),
    Form("Orbit", "@"),
    List("Decompose", "≡"),
    Checkout("Nested", "€"),
}
