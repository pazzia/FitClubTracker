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

/** Thumbnails retain the full photograph. Foreground pixels stay unchanged;
 * the background has 90% of its original contrast, without blur or fading. */
internal fun renderExercisePhoto(source: Bitmap, matte: Bitmap): Bitmap {
    val w = source.width; val h = source.height
    val scaled = if (matte.width == w && matte.height == h) matte
        else Bitmap.createScaledBitmap(matte, w, h, true)
    val pixels = IntArray(w * h)
    val mask = IntArray(pixels.size)
    source.getPixels(pixels, 0, w, 0, 0, w, h)
    scaled.getPixels(mask, 0, w, 0, 0, w, h)
    val result = IntArray(pixels.size)
    for (i in pixels.indices) {
        val subject = Color.red(mask[i]) / 255f
        val c = pixels[i]
        fun channel(value: Int): Int {
            val muted = 127.5f + (value - 127.5f) * .9f
            return (value * subject + muted * (1f - subject)).roundToInt().coerceIn(0, 255)
        }
        result[i] = Color.argb(Color.alpha(c), channel(Color.red(c)),
            channel(Color.green(c)), channel(Color.blue(c)))
    }
    if (scaled !== matte) scaled.recycle()
    return Bitmap.createBitmap(result, w, h, Bitmap.Config.ARGB_8888)
}
