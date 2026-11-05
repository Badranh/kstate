package dev.kstate.host.desktop

import java.nio.file.Path

/** Where recordings live: made here in [recordings], opened from a `.kstate` file into [imports]. */
class RecordingLibrary(
    root: Path = defaultRoot(),
) {
    val recordings: Path = root.resolve("Recordings")
    val imports: Path = root.resolve("Imported")

    private companion object {
        fun defaultRoot(): Path = Path.of(System.getProperty("user.home"), "Documents", "Kstate")
    }
}
