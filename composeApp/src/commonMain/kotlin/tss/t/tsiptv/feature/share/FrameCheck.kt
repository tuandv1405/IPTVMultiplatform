package tss.t.tsiptv.feature.share

import androidx.compose.ui.graphics.ImageBitmap

/**
 * A protected (secure surface) or not yet drawn video reads back as a black picture: the share card
 * then shows the channel logo instead.
 */
object FrameCheck {
    /** Rows read across the picture; a whole frame would be millions of pixels. */
    private val SAMPLE_ROWS = floatArrayOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)

    /** Brightest luma (0–255) a "black" picture may have: encoder noise, not content. */
    private const val MAX_BLANK_LUMA = 20

    fun isBlank(image: ImageBitmap): Boolean {
        if (image.width <= 0 || image.height <= 0) return true
        val pixels = IntArray(image.width * SAMPLE_ROWS.size)
        SAMPLE_ROWS.forEachIndexed { i, y ->
            val row = (image.height * y).toInt().coerceIn(0, image.height - 1)
            image.readPixels(pixels, startX = 0, startY = row, width = image.width, height = 1, bufferOffset = i * image.width)
        }
        return isBlank(pixels)
    }

    /** [argb] pixels as `0xAARRGGBB`. */
    fun isBlank(argb: IntArray): Boolean = argb.all { luma(it) <= MAX_BLANK_LUMA }

    private fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
