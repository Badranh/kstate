package dev.kstate.viewer.ui.screen

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import dev.kstate.core.model.VideoFrame
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/** One reused Skia bitmap per tile, reallocated only when the frame size changes. */
internal class FrameBitmap {
    private var bitmap: Bitmap? = null
    private var image: ImageBitmap? = null

    fun show(frame: VideoFrame): ImageBitmap {
        val info = ImageInfo(frame.width, frame.height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
        val current =
            bitmap?.takeIf { it.width == frame.width && it.height == frame.height } ?: Bitmap().also {
                it.allocPixels(info)
                bitmap?.close()
                bitmap = it
                image = it.asComposeImageBitmap()
            }
        current.installPixels(info, frame.rgba, frame.width * BYTES_PER_PIXEL)
        current.notifyPixelsChanged()
        return checkNotNull(image)
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4
    }
}
