package dev.kstate.host.desktop

import java.nio.file.Path
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreferencesSaveFoldersTest {
    private val node: Preferences = Preferences.userRoot().node("dev/kstate/test-${UUID.randomUUID()}")

    @AfterTest
    fun removeNode() = node.removeNode()

    @Test
    fun theLastFolderIsRememberedAcrossInstances() {
        PreferencesSaveFolders(node).last = Path.of("/Users/ada/Bugs")

        assertEquals(Path.of("/Users/ada/Bugs"), PreferencesSaveFolders(node).last)
    }

    @Test
    fun nothingIsRememberedAtFirst() {
        assertNull(PreferencesSaveFolders(node).last)
    }
}
