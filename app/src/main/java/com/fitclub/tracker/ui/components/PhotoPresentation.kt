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

/** Processing is exclusively for thumbnails; source pixels and detail photos are untouched. */
internal fun renderExercisePhoto(source: Bitmap, matte: Bitmap, clothing: Bitmap? = null): Bitmap {
    val w = source.width; val h = source.height
    val scaled = if (matte.width == w && matte.height == h) matte
        else Bitmap.createScaledBitmap(matte, w, h, true)
    val pixels = IntArray(w * h)
    val mask = IntArray(pixels.size)
    source.getPixels(pixels, 0, w, 0, 0, w, h)
    scaled.getPixels(mask, 0, w, 0, 0, w, h)
    val garment = IntArray(pixels.size)
    if (clothing != null) {
        val aligned = if (clothing.width == w && clothing.height == h) clothing
            else Bitmap.createScaledBitmap(clothing, w, h, false)
        aligned.getPixels(garment, 0, w, 0, 0, w, h)
        if (aligned !== clothing) aligned.recycle()
    }
    val target = chooseGarmentColor(pixels, mask, garment, w, h)
    val blurred = blurThumbnailBackground(pixels, w, h, maxOf(1, w / 140))
    val result = IntArray(pixels.size)
    val hsv = FloatArray(3)
    for (i in pixels.indices) {
        val subject = Color.red(mask[i]) / 255f
        val c = pixels[i]
        var front = c
        if (target != null && Color.red(garment[i]) >= 240 && subject >= .94f) {
            Color.colorToHSV(c, hsv)
            // Keep white printed logos on otherwise dark garments.
            if (!(hsv[2] > .87f && hsv[1] < .12f && target[2] < .55f)) {
                val luminance = (Color.red(c)*.299f + Color.green(c)*.587f + Color.blue(c)*.114f) / 255f
                val shade = (.82f + (luminance - target[2]) * .75f).coerceIn(.38f, 1f)
                front = Color.rgb((target[0] * shade).roundToInt(), (target[1] * shade).roundToInt(),
                    (target[3] * shade).roundToInt())
            }
        }
        fun channel(value: Int, foreground: Int): Int {
            val muted = (127.5f + (value - 127.5f) * .9f) * .9f + 25.5f
            return (foreground * subject + muted * (1f - subject)).roundToInt().coerceIn(0, 255)
        }
        val bg = blurred[i]
        result[i] = Color.argb(Color.alpha(c), channel(Color.red(bg), Color.red(front)),
            channel(Color.green(bg), Color.green(front)), channel(Color.blue(bg), Color.blue(front)))
    }
    if (scaled !== matte) scaled.recycle()
    return Bitmap.createBitmap(result, w, h, Bitmap.Config.ARGB_8888)
}

/** The comparison uses nearby background, not the photograph's global average. */
private fun chooseGarmentColor(pixels: IntArray, foreground: IntArray, garment: IntArray, w: Int, h: Int): FloatArray? {
    val shirt = FloatArray(3); val background = FloatArray(3)
    var count = 0; var left = w; var right = -1; var top = h; var bottom = -1
    for (i in pixels.indices) if (Color.red(garment[i]) >= 240 && Color.red(foreground[i]) >= 240) {
        shirt[0] += Color.red(pixels[i]); shirt[1] += Color.green(pixels[i]); shirt[2] += Color.blue(pixels[i])
        count++; left = minOf(left, i % w); right = maxOf(right, i % w)
        top = minOf(top, i / w); bottom = maxOf(bottom, i / w)
    }
    if (count < maxOf(12, pixels.size / 1000)) return null
    for (n in 0..2) shirt[n] /= count
    var bgCount = 0
    val margin = maxOf(12, (right-left+bottom-top)/3)
    for (y in maxOf(0, top-margin)..minOf(h-1, bottom+margin))
        for (x in maxOf(0, left-margin)..minOf(w-1, right+margin)) {
            val i = y*w+x
            if (Color.red(foreground[i]) < 40) {
                background[0] += Color.red(pixels[i]); background[1] += Color.green(pixels[i]); background[2] += Color.blue(pixels[i]); bgCount++
            }
        }
    if (bgCount < 12) return null
    for (n in 0..2) background[n] /= bgCount
    fun luminance(rgb: FloatArray) = (rgb[0]*.299f + rgb[1]*.587f + rgb[2]*.114f) / 255f
    fun distance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (n in 0..2) { val difference = (a[n]-b[n])/255f; sum += difference*difference }
        return kotlin.math.sqrt(sum)
    }
    val a = luminance(shirt); val b = luminance(background)
    val contrast = (maxOf(a,b)+.05f)/(minOf(a,b)+.05f)
    if (contrast >= 2.2f || distance(shirt, background) >= .55f) return null
    val palette = arrayOf(floatArrayOf(255f,120f,20f), floatArrayOf(0f,220f,240f),
        floatArrayOf(235f,65f,180f), floatArrayOf(55f,145f,255f))
    // Evaluate the displayed shade, not an unrealistic full-brightness swatch.
    val target = palette.maxBy { color ->
        val displayed = FloatArray(3) { color[it] * .82f }
        distance(displayed, background) + kotlin.math.abs(luminance(displayed)-b)
    }
    return floatArrayOf(target[0], target[1], a, target[2])
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
