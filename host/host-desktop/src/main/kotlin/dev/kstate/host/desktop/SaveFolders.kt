package dev.kstate.host.desktop

import java.nio.file.Path
import java.util.prefs.Preferences

/** The folder the user last saved a recording to, so the next Save dialog starts there. */
interface SaveFolders {
    var last: Path?
}

/** Kept in the user's preferences, so it survives a restart. */
class PreferencesSaveFolders(
    private val node: Preferences = Preferences.userRoot().node("dev/kstate"),
) : SaveFolders {
    override var last: Path?
        get() = node.get(KEY, null)?.let(Path::of)
        set(value) {
            if (value == null) node.remove(KEY) else node.put(KEY, value.toString())
            node.flush()
        }

    private companion object {
        const val KEY = "lastSaveFolder"
    }
}
