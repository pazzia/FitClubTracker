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

/** Preserve foreground and active equipment; gently brighten and blur only the background. */
internal fun renderExercisePhoto(source: Bitmap, matte: Bitmap): Bitmap {
    val w = source.width; val h = source.height
    val scaled = if (matte.width == w && matte.height == h) matte
        else Bitmap.createScaledBitmap(matte, w, h, true)
    val pixels = IntArray(w * h)
    val mask = IntArray(pixels.size)
    source.getPixels(pixels, 0, w, 0, 0, w, h)
    scaled.getPixels(mask, 0, w, 0, 0, w, h)
    val blurred = blurThumbnailBackground(pixels, w, h, maxOf(1, w / 140))
    val result = IntArray(pixels.size)
    for (i in pixels.indices) {
        val subject = Color.red(mask[i]) / 255f
        val c = pixels[i]; val bg = blurred[i]
        fun channel(value: Int, foreground: Int): Int {
            val muted = (127.5f + (value - 127.5f) * .9f) * .9f + 25.5f
            return (foreground * subject + muted * (1f - subject)).roundToInt().coerceIn(0, 255)
        }
        result[i] = Color.argb(Color.alpha(c), channel(Color.red(bg), Color.red(c)),
            channel(Color.green(bg), Color.green(c)), channel(Color.blue(bg), Color.blue(c)))
    }
    if (scaled !== matte) scaled.recycle()
    return Bitmap.createBitmap(result, w, h, Bitmap.Config.ARGB_8888)
}

/** Small separable box blur; final compositing restores all foreground pixels. */
private fun blurThumbnailBackground(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
    fun pass(input: IntArray, horizontal: Boolean): IntArray {
        val out = IntArray(input.size)
        val length = if (horizontal) w else h
        val lines = if (horizontal) h else w
        fun index(line: Int, position: Int) = if (horizontal) line*w+position else position*w+line
        val count = 2*radius+1
        for (line in 0 until lines) {
            var r = 0; var g = 0; var b = 0
            for (offset in -radius..radius) {
                val c = input[index(line, offset.coerceIn(0,length-1))]
                r += Color.red(c); g += Color.green(c); b += Color.blue(c)
            }
            for (position in 0 until length) {
                out[index(line,position)] = Color.rgb(r/count,g/count,b/count)
                val old = input[index(line,(position-radius).coerceIn(0,length-1))]
                val next = input[index(line,(position+radius+1).coerceIn(0,length-1))]
                r += Color.red(next)-Color.red(old); g += Color.green(next)-Color.green(old); b += Color.blue(next)-Color.blue(old)
            }
        }
        return out
    }
    return pass(pass(pixels,true),false)
}


/** Crop only empty context, keeping every foreground mask pixel and a visible margin. */
internal fun frameExerciseThumbnail(source: Bitmap, matte: Bitmap): Bitmap {
    val w = source.width; val h = source.height
    val scaled = if (matte.width == w && matte.height == h) matte else Bitmap.createScaledBitmap(matte, w, h, false)
    val pixels = IntArray(w * h); scaled.getPixels(pixels, 0, w, 0, 0, w, h)
    var left = w; var right = -1; var top = h; var bottom = -1
    pixels.forEachIndexed { i, value -> if (Color.red(value) >= 128) {
        val x = i % w; val y = i / w
        left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
    } }
    if (scaled !== matte) scaled.recycle()
    if (right < left || bottom < top) return source
    val margin = (maxOf(right - left + 1, bottom - top + 1) * .08f).roundToInt().coerceAtLeast(2)
    left = (left - margin).coerceAtLeast(0); right = (right + margin).coerceAtMost(w - 1)
    top = (top - margin).coerceAtLeast(0); bottom = (bottom + margin).coerceAtMost(h - 1)
    // Expand the shorter axis toward a common 3:2 frame; never shrink the subject bounds.
    val targetW = maxOf(right - left + 1, ((bottom - top + 1) * 1.5f).roundToInt()).coerceAtMost(w)
    val targetH = maxOf(bottom - top + 1, ((right - left + 1) / 1.5f).roundToInt()).coerceAtMost(h)
    left = ((left + right + 1 - targetW) / 2).coerceIn(0, w - targetW)
    top = ((top + bottom + 1 - targetH) / 2).coerceIn(0, h - targetH)
    if (left == 0 && top == 0 && targetW == w && targetH == h) return source
    return Bitmap.createBitmap(source, left, top, targetW, targetH)
}
