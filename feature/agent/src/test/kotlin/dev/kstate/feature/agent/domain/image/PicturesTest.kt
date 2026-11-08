package dev.kstate.feature.agent.domain.image

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PicturesTest {
    /** [paint] gives each pixel's grey level from its position. */
    private fun picture(
        width: Int = 90,
        height: Int = 160,
        paint: (Int, Int) -> Int,
    ): Picture {
        val rgba = ByteArray(width * height * 4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val grey = paint(x, y).coerceIn(0, 255).toByte()
                val i = (y * width + x) * 4
                rgba[i] = grey
                rgba[i + 1] = grey
                rgba[i + 2] = grey
                rgba[i + 3] = -1
            }
        }
        return Picture(width, height, rgba)
    }

    private val gradient = picture { x, y -> x * 2 + y }
    private val card = picture { x, y -> if (y in 40..90 && x in 10..80) 230 else 30 }

    private fun same(
        a: Picture,
        b: Picture,
    ) = Fingerprints.sameScreen(Fingerprints.of(a), Fingerprints.of(b))

    @Test
    fun aPictureIsItsOwnScreenAndADifferentLayoutIsNot() {
        assertTrue(same(card, card))
        assertFalse(same(gradient, card))
    }

    @Test
    fun noiseAndOneStrayPixelAreStillTheSameScreen() {
        val noisy = picture { x, y -> (if (y in 40..90 && x in 10..80) 230 else 30) + (x + y) % 3 }
        val stray =
            picture { x, y ->
                if (x == 3 && y == 3) {
                    255
                } else if (y in 40..90 && x in 10..80) {
                    230
                } else {
                    30
                }
            }

        assertTrue(same(card, noisy))
        assertTrue(same(card, stray))
    }

    @Test
    fun aShortChangeOfTextIsNoticed() {
        val price =
            picture { x, y ->
                if (y in 120..126 &&
                    x in 60..72
                ) {
                    230
                } else if (y in 40..90 && x in 10..80) {
                    230
                } else {
                    30
                }
            }

        assertFalse(same(card, price))
    }

    @Test
    fun aFlatScreenIsBlankAndARealOneIsNot() {
        assertTrue(Fingerprints.isBlank(picture { _, _ -> 250 }))
        assertTrue(Fingerprints.isBlank(picture { x, _ -> if (x == 0) 0 else 250 }))
        assertFalse(Fingerprints.isBlank(card))
    }

    @Test
    fun aPngDecodesBackAtTheAskedWidth() {
        val png = Pngs.of(card, maxWidth = 45)
        val image = ImageIO.read(ByteArrayInputStream(png))

        assertEquals(45, image.width)
        assertEquals(80, image.height)
        assertEquals(0xE6, image.getRGB(22, 32) and 0xFF)
    }

    @Test
    fun aContactSheetLaysTilesOutInRowsWithLabels() {
        val sheet = Pngs.contactSheet(List(5) { "v${it + 1}" to card }, columns = 3, tileWidth = 60)
        val image = ImageIO.read(ByteArrayInputStream(sheet))

        assertEquals(3 * 60 + 4 * Pngs.SHEET_GAP, image.width)
        assertTrue(image.height > 2 * (60 * 160 / 90))
    }

    /** A tall phone-sized screen with a text caret: a 2 px wide, 17 px tall bar, as at a real tile size. */
    private fun withCaret(shown: Boolean) =
        picture(width = 360, height = 780) { x, y ->
            when {
                shown && x in 120..121 && y in 300..316 -> 255
                y in 290..326 && x in 20..340 -> 60
                else -> 20
            }
        }

    @Test
    fun theSameScreenCheckStaysStrictSoASmallTextChangeIsNeverCalledUnchanged() {
        assertFalse(Fingerprints.sameScreen(Fingerprints.of(withCaret(true)), Fingerprints.of(withCaret(false))))
    }

    @Test
    fun aCaretBlinkIsSmallMotionButANewLineOfTextIsNot() {
        val text =
            picture(width = 360, height = 780) { x, y ->
                if (y in 300..316 &&
                    x in 40..200
                ) {
                    255
                } else if (y in 290..326 && x in 20..340) {
                    60
                } else {
                    20
                }
            }

        assertTrue(Fingerprints.isSmallMotion(Fingerprints.of(withCaret(true)), Fingerprints.of(withCaret(false))))
        assertFalse(Fingerprints.isSmallMotion(Fingerprints.of(withCaret(false)), Fingerprints.of(text)))
    }
}
