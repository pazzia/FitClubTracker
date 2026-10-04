package com.fitclub.tracker.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

internal fun trimPhotoPadding(source: Bitmap, reference: Bitmap = source): Bitmap {
    val w = source.width; val h = source.height
    val pixels = IntArray(w * h)
    require(reference.width == w && reference.height == h)
    reference.getPixels(pixels, 0, w, 0, 0, w, h)
    fun white(c: Int) = Color.red(c) >= 244 && Color.green(c) >= 244 && Color.blue(c) >= 244
    if (pixels.all { white(it) }) return source
    fun row(y: Int) = (0 until w).count { white(pixels[y * w + it]) } >= w * .995
    fun column(x: Int) = (0 until h).count { white(pixels[it * w + x]) } >= h * .995
    var top = 0; var bottom = h - 1; var left = 0; var right = w - 1
    while (top < h / 4 && row(top)) top++
    while (bottom > h * 3 / 4 && row(bottom)) bottom--
    while (left < w / 4 && column(left)) left++
    while (right > w * 3 / 4 && column(right)) right--
    if (left == 0 && top == 0 && right == w - 1 && bottom == h - 1) return source
    return Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1)
}

/** Uses a precomputed, per-pixel foreground matte including the athlete and active equipment.
 * Large photographs and thumbnails share this renderer. No person bounding box is painted. */
internal fun renderExercisePhoto(source: Bitmap, matte: Bitmap): Bitmap {
    val scaled = if (matte.width == source.width && matte.height == source.height) matte
        else Bitmap.createScaledBitmap(matte, source.width, source.height, true)
    val count = source.width * source.height
    val pixels = IntArray(count)
    val mask = IntArray(count)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    scaled.getPixels(mask, 0, source.width, 0, 0, source.width, source.height)
    for (i in pixels.indices) {
        val c = pixels[i]
        val subject = Color.red(mask[i]) / 255f
        val gray = .299f * Color.red(c) + .587f * Color.green(c) + .114f * Color.blue(c)
        val saturation = .18f + .9f * subject
        val contrast = .72f + .44f * subject
        fun channel(value: Int): Int = (((gray + (value - gray) * saturation) - 128f) * contrast + 128f)
            .roundToInt().coerceIn(0, 255)
        val opacity = (.20f + .80f * subject) * Color.alpha(c)
        pixels[i] = Color.argb(opacity.roundToInt().coerceIn(0, 255), channel(Color.red(c)),
            channel(Color.green(c)), channel(Color.blue(c)))
    }
    if (scaled !== matte) scaled.recycle()
    return Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
}
