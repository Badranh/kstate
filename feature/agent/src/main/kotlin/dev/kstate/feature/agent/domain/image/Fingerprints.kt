package dev.kstate.feature.agent.domain.image

import kotlin.math.abs
import kotlin.math.sqrt

/** A screen reduced to a grid of brightness levels, to compare screens without looking at them. */
class Fingerprint(
    val cells: IntArray,
)

/** Is it the same screen, is it empty: what Kstate can tell about a screen on its own. */
object Fingerprints {
    private const val GRID_WIDTH = 32
    private const val GRID_HEIGHT = 64

    /** A cell counts as changed when its brightness moves more than this: past video noise. */
    private const val CELL_CHANGE = 6

    /** One changed cell may be a blinking caret; more is a different screen. */
    private const val STRAY_CELLS = 1

    /** A caret at a real tile size covers two or three cells of one column, a spinner a few more. */
    private const val MOTION_CELLS = 6
    private const val MOTION_COLUMNS = 2
    private const val BLANK_SPREAD = 6.0
    private const val BLANK_SHARE = 0.985
    private const val SAMPLES = 20_000

    /** The screen's average brightness in a 32 by 64 grid: fine enough to see a changed price. */
    fun of(picture: Picture): Fingerprint {
        val sums = DoubleArray(GRID_WIDTH * GRID_HEIGHT)
        val counts = IntArray(sums.size)
        for (y in 0 until picture.height) {
            val row = y * GRID_HEIGHT / picture.height * GRID_WIDTH
            for (x in 0 until picture.width) {
                val cell = row + x * GRID_WIDTH / picture.width
                sums[cell] += picture.luma(x, y)
                counts[cell]++
            }
        }
        return Fingerprint(IntArray(sums.size) { (sums[it] / maxOf(1, counts[it])).toInt() })
    }

    fun changedCells(
        a: Fingerprint,
        b: Fingerprint,
    ): Int = a.cells.indices.count { abs(a.cells[it] - b.cells[it]) > CELL_CHANGE }

    fun sameScreen(
        a: Fingerprint,
        b: Fingerprint,
    ): Boolean = changedCells(a, b) <= STRAY_CELLS

    /**
     * The only change is a few cells in one or two adjacent columns: a blinking caret or a spinner,
     * or a small piece of text that changed. One comparison can't tell those apart, so this never
     * decides that a screen is unchanged: it only keeps a screen from waiting to settle.
     */
    fun isSmallMotion(
        a: Fingerprint,
        b: Fingerprint,
    ): Boolean {
        val changed = a.cells.indices.filter { abs(a.cells[it] - b.cells[it]) > CELL_CHANGE }
        if (changed.size > MOTION_CELLS) return false
        val columns = changed.map { it % GRID_WIDTH }
        return columns.isEmpty() || columns.max() - columns.min() < MOTION_COLUMNS
    }

    /** Nearly every pixel is the same shade: a white or black screen, perhaps with a thin bar. */
    fun isBlank(picture: Picture): Boolean {
        val pixels = picture.width * picture.height
        val samples =
            (0 until pixels step maxOf(1, pixels / SAMPLES)).map {
                picture.luma(
                    it % picture.width,
                    it / picture.width,
                )
            }
        val mean = samples.average()
        val near = samples.count { abs(it - mean) <= BLANK_SPREAD * 2 }
        val spread = sqrt(samples.sumOf { (it - mean) * (it - mean) } / samples.size)
        return spread <= BLANK_SPREAD || near >= samples.size * BLANK_SHARE
    }
}
