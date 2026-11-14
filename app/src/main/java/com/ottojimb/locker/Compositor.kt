package com.ottojimb.locker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.HardwareBuffer

object Compositor {

    /** How the overlay colors are changed before drawing. */
    enum class Invert {
        NONE,

        /** Plain RGB negation (for a dark theme without a display color transform). */
        NEGATE,

        /**
         * Android's display color inversion (Bigme's dark mode). It inverts lightness but keeps
         * hue, so plain negation ends up with shifted hues. That matrix is its own inverse, so
         * applying it here makes the panel show the image with its original colors.
         */
        MATCH_SYSTEM,
    }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    // ColorMatrix rows are R, G, B, A with an offset in 0..255; alpha is kept so transparency stays.
    private val negatePaint = filtered(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** AOSP DisplayTransformManager.MATRIX_INVERT_COLOR, transposed to row-major. */
    private val matchSystemPaint = filtered(
        0.402f, -1.174f, -0.228f, 0f, 255f,
        -0.598f, -0.174f, -0.228f, 0f, 255f,
        -0.599f, -1.175f, 0.772f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )

    private fun filtered(vararg matrix: Float) = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
    }

    /** Copies a screenshot buffer into a mutable software bitmap and closes the buffer. */
    fun toBitmap(buffer: HardwareBuffer, colorSpace: ColorSpace?): Bitmap? = buffer.use {
        Bitmap.wrapHardwareBuffer(it, colorSpace)?.let { hw ->
            hw.copy(Bitmap.Config.ARGB_8888, true).also { hw.recycle() }
        }
    }

    /** Draws the overlay over the screenshot (in place), optionally with inverted colors. */
    fun compose(shot: Bitmap, overlay: Bitmap?, invert: Invert = Invert.NONE): Bitmap {
        if (overlay != null) {
            val p = when (invert) {
                Invert.NONE -> paint
                Invert.NEGATE -> negatePaint
                Invert.MATCH_SYSTEM -> matchSystemPaint
            }
            Canvas(shot).drawBitmap(overlay, null, target(shot, overlay), p)
        }
        return shot
    }

    /**
     * Same orientation: stretch over the whole screen (small size differences are not worth bars).
     * Different orientation (e.g. a portrait image while locking in landscape): keep the aspect
     * ratio and center it, so the page shows on the sides instead of the image being distorted.
     */
    private fun target(shot: Bitmap, overlay: Bitmap): Rect {
        val shotLandscape = shot.width > shot.height
        val overlayLandscape = overlay.width > overlay.height
        if (shotLandscape == overlayLandscape) return Rect(0, 0, shot.width, shot.height)
        val scale = minOf(shot.width.toFloat() / overlay.width, shot.height.toFloat() / overlay.height)
        val w = (overlay.width * scale).toInt()
        val h = (overlay.height * scale).toInt()
        val left = (shot.width - w) / 2
        val top = (shot.height - h) / 2
        return Rect(left, top, left + w, top + h)
    }
}
