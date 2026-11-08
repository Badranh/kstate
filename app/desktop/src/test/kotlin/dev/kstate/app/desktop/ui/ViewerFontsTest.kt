package dev.kstate.app.desktop.ui

import java.awt.Font
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guests' browsers draw the web viewer with fonts it brings along; without them, an app state
 * holding emoji or Chinese, Japanese or Korean showed empty boxes. Read from what the desktop
 * serves, so the packaging is checked too.
 */
class ViewerFontsTest {
    private val samples = listOf("😀", "🎉", "日本語", "中文", "한국어", "←", "→", "⌘", "★")

    @Test
    fun theWebViewerBringsFontsForWhatAppStatesHold() {
        val fonts = servedFonts().map { Font.createFont(Font.TRUETYPE_FONT, it.toFile()) }

        val missing = samples.filter { sample -> fonts.none { it.canDisplayUpTo(sample) == -1 } }

        assertTrue(missing.isEmpty(), "no bundled font draws $missing (fonts: ${fonts.map { it.fontName }})")
    }

    private fun servedFonts(): List<Path> {
        val viewer = checkNotNull(javaClass.getResource("/viewer/index.html")) { "the web viewer is not packaged" }
        val root =
            if (viewer.protocol == "jar") {
                FileSystems.newFileSystem(viewer.toURI(), emptyMap<String, Any>()).getPath("/viewer")
            } else {
                Paths.get(viewer.toURI()).parent
            }
        return Files.walk(root).use { paths -> paths.filter { it.extension in setOf("ttf", "otf") }.toList() }
    }
}
