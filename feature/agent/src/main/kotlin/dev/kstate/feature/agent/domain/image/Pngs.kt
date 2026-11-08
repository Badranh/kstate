package dev.kstate.feature.agent.domain.image

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** Screens as PNG: one at a time, scaled for an agent that pays for every pixel, or many on a sheet. */
object Pngs {
    const val SHEET_GAP: Int = 8
    private const val LABEL_HEIGHT = 22
    private const val LABEL_FONT = 14
    private const val LABEL_INSET = 2
    private const val SHEET_BACKGROUND = 0x0A0C11

    fun of(
        picture: Picture,
        maxWidth: Int = picture.width,
    ): ByteArray = encode(scaled(image(picture), maxWidth))

    /** [png] made at most [maxWidth] wide. */
    fun scaled(
        png: ByteArray,
        maxWidth: Int,
    ): ByteArray = encode(scaled(decode(png), maxWidth))

    /** Tiles in rows of [columns], each with its label underneath, so one image shows many states. */
    fun contactSheet(
        tiles: List<Pair<String, Picture>>,
        columns: Int,
        tileWidth: Int,
    ): ByteArray = sheet(tiles.map { (label, picture) -> label to image(picture) }, columns, tileWidth)

    /** The same, from PNGs such as captures keep. */
    fun contactSheetOfPngs(
        tiles: List<Pair<String, ByteArray>>,
        columns: Int,
        tileWidth: Int,
    ): ByteArray = sheet(tiles.map { (label, png) -> label to decode(png) }, columns, tileWidth)

    private fun sheet(
        tiles: List<Pair<String, BufferedImage>>,
        columns: Int,
        tileWidth: Int,
    ): ByteArray {
        val scaledTiles = tiles.map { (label, image) -> label to scaled(image, tileWidth) }
        val tileHeight = scaledTiles.maxOfOrNull { it.second.height } ?: 0
        val rows = (tiles.size + columns - 1) / columns
        val sheet =
            BufferedImage(
                columns * tileWidth + (columns + 1) * SHEET_GAP,
                rows * (tileHeight + LABEL_HEIGHT) + (rows + 1) * SHEET_GAP,
                BufferedImage.TYPE_INT_RGB,
            )
        val g = sheet.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(SHEET_BACKGROUND)
        g.fillRect(0, 0, sheet.width, sheet.height)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, LABEL_FONT)
        scaledTiles.forEachIndexed { index, (label, tile) ->
            val x = SHEET_GAP + (index % columns) * (tileWidth + SHEET_GAP)
            val y = SHEET_GAP + (index / columns) * (tileHeight + LABEL_HEIGHT + SHEET_GAP)
            g.drawImage(tile, x, y, null)
            g.color = Color.WHITE
            g.drawString(label, x + LABEL_INSET, y + tileHeight + LABEL_FONT + LABEL_INSET)
        }
        g.dispose()
        return encode(sheet)
    }

    private fun image(picture: Picture): BufferedImage {
        val image = BufferedImage(picture.width, picture.height, BufferedImage.TYPE_INT_RGB)
        val pixels = IntArray(picture.width * picture.height) { picture.rgb(it) }
        image.setRGB(0, 0, picture.width, picture.height, pixels, 0, picture.width)
        return image
    }

    private fun scaled(
        image: BufferedImage,
        width: Int,
    ): BufferedImage {
        if (width >= image.width) return image
        val height = maxOf(1, image.height * width / image.width)
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(image, 0, 0, width, height, null)
        g.dispose()
        return out
    }

    private fun decode(png: ByteArray): BufferedImage =
        checkNotNull(ImageIO.read(ByteArrayInputStream(png))) {
            "Not a picture"
        }

    private fun encode(image: BufferedImage): ByteArray =
        ByteArrayOutputStream()
            .also {
                ImageIO.write(image, "png", it)
            }.toByteArray()
}
