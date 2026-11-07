package dev.kstate.viewer.ui.screen

import androidx.compose.runtime.snapshots.Snapshot
import dev.kstate.viewer.client.VideoFrame
import kotlin.test.Test
import kotlin.test.assertTrue

class TilePictureTest {
    private fun frame(
        shade: Byte,
        hostTs: Long,
    ) = VideoFrame(2, 2, ByteArray(2 * 2 * 4) { shade }, hostTs)

    @Test
    fun aNewPictureOfTheSameSizeTellsTheTileToRedraw() {
        val picture = TilePicture()
        picture.show(frame(shade = 0, hostTs = 1))
        Snapshot.sendApplyNotifications()

        val changed = mutableSetOf<Any>()
        val observer = Snapshot.registerApplyObserver { written, _ -> changed.addAll(written) }
        try {
            picture.show(frame(shade = 1, hostTs = 2))
            Snapshot.sendApplyNotifications()
        } finally {
            observer.dispose()
        }

        assertTrue(changed.isNotEmpty(), "the second picture changed no state, so nothing redraws")
    }
}
