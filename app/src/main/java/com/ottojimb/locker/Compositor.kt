package com.ottojimb.locker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.HardwareBuffer

object Compositor {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Copies a screenshot buffer into a mutable software bitmap and closes the buffer. */
    fun toBitmap(buffer: HardwareBuffer, colorSpace: ColorSpace?): Bitmap? = buffer.use {
        Bitmap.wrapHardwareBuffer(it, colorSpace)?.let { hw ->
            hw.copy(Bitmap.Config.ARGB_8888, true).also { hw.recycle() }
        }
    }

    /** Draws the overlay stretched over the whole screenshot (in place). */
    fun compose(shot: Bitmap, overlay: Bitmap?): Bitmap {
        if (overlay != null) {
            Canvas(shot).drawBitmap(overlay, null, Rect(0, 0, shot.width, shot.height), paint)
        }
        return shot
    }
}
