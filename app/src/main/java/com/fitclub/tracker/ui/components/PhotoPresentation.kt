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

/** Only thumbnails use this renderer. The foreground keeps original pixels and all
 * apparatus included in its pixel matte; gaps reveal a blurred, quarter-opacity gym. */
internal fun renderExercisePhoto(source: Bitmap, matte: Bitmap): Bitmap {
    val w = source.width; val h = source.height
    val scaled = if (matte.width == w && matte.height == h) matte
        else Bitmap.createScaledBitmap(matte, w, h, true)
    val pixels = IntArray(w * h)
    val mask = IntArray(pixels.size)
    source.getPixels(pixels, 0, w, 0, 0, w, h)
    scaled.getPixels(mask, 0, w, 0, 0, w, h)
    val blurred = blurPhotoBackground(pixels, w, h, maxOf(2, minOf(w, h) / 65))
    val result = IntArray(pixels.size)
    for (i in pixels.indices) {
        val subject = Color.red(mask[i]) / 255f
        val c = pixels[i]
        val bg = blurred[i]
        val gray = .299f * Color.red(bg) + .587f * Color.green(bg) + .114f * Color.blue(bg)
        val foregroundAlpha = subject * Color.alpha(c) / 255f
        val backgroundAlpha = .25f * (1f - subject) * Color.alpha(bg) / 255f
        val alpha = foregroundAlpha + backgroundAlpha
        fun channel(value: Int, background: Int): Int {
            val muted = gray + (background - gray) * .18f
            return if (alpha == 0f) 0 else ((value * foregroundAlpha + muted * backgroundAlpha) / alpha)
                .roundToInt().coerceIn(0, 255)
        }
        result[i] = Color.argb((alpha * 255).roundToInt(), channel(Color.red(c), Color.red(bg)),
            channel(Color.green(c), Color.green(bg)), channel(Color.blue(c), Color.blue(bg)))
    }
    if (scaled !== matte) scaled.recycle()
    return Bitmap.createBitmap(result, w, h, Bitmap.Config.ARGB_8888)
}

/** Sliding separable box blur, three passes: bounded memory and linear work. */
private fun blurPhotoBackground(source: IntArray, w: Int, h: Int, radius: Int): IntArray {
    var input = source
    repeat(3) {
        val horizontal = IntArray(source.size)
        val vertical = IntArray(source.size)
        fun pass(src: IntArray, dst: IntArray, lines: Int, length: Int, step: Int, lineStep: Int) {
            val size = radius * 2 + 1
            for (line in 0 until lines) {
                val offset = line * lineStep
                var red = 0; var green = 0; var blue = 0
                fun add(position: Int, sign: Int) {
                    val color = src[offset + position.coerceIn(0, length - 1) * step]
                    red += Color.red(color) * sign
                    green += Color.green(color) * sign
                    blue += Color.blue(color) * sign
                }
                for (position in -radius..radius) add(position, 1)
                for (position in 0 until length) {
                    dst[offset + position * step] = Color.rgb(red / size, green / size, blue / size)
                    add(position - radius, -1)
                    add(position + radius + 1, 1)
                }
            }
        }
        pass(input, horizontal, h, w, 1, w)
        pass(horizontal, vertical, w, h, w, 1)
        input = vertical
    }
    return input
}
