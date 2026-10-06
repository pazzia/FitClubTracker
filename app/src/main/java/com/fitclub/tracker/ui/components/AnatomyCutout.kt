package com.fitclub.tracker.ui.components

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlin.math.abs

/** Remove only the atlas backdrop connected to the frame; enclosed dark clothing stays intact. */
internal object AnatomyCutouts {
    private val cache = object : LruCache<Int, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.allocationByteCount
    }

    @Synchronized fun get(resources: Resources, resource: Int): Bitmap {
        cache.get(resource)?.let { return it }
        val source = requireNotNull(BitmapFactory.decodeResource(resources, resource))
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val cutout = trimAtlasBackdrop(pixels, source.width, source.height)
        val result = Bitmap.createBitmap(cutout.pixels, cutout.width, cutout.height, Bitmap.Config.ARGB_8888)
        source.recycle()
        cache.put(resource, result)
        return result
    }
}

internal data class AtlasCutout(val pixels: IntArray, val width: Int, val height: Int)

internal fun trimAtlasBackdrop(source: IntArray, width: Int, height: Int): AtlasCutout {
    require(width > 0 && height > 0 && source.size == width * height)
    val pixels = source.copyOf()
    val visited = BooleanArray(pixels.size)
    val queue = IntArray(pixels.size)
    var tail = 0
    fun enqueue(index: Int) {
        if (visited[index]) return
        visited[index] = true
        val color = pixels[index]
        val alpha = color ushr 24
        // The source WebP backdrop is #14191D with small compression variations.
        if (alpha == 0 || (abs(((color ushr 16) and 255) - 20) <= 18 &&
                abs(((color ushr 8) and 255) - 25) <= 18 && abs((color and 255) - 29) <= 18)) {
            pixels[index] = 0
            queue[tail++] = index
        }
    }
    for (x in 0 until width) { enqueue(x); enqueue((height - 1) * width + x) }
    for (y in 0 until height) { enqueue(y * width); enqueue(y * width + width - 1) }
    var head = 0
    while (head < tail) {
        val index = queue[head++]
        val x = index % width
        if (x > 0) enqueue(index - 1)
        if (x + 1 < width) enqueue(index + 1)
        if (index >= width) enqueue(index - width)
        if (index + width < pixels.size) enqueue(index + width)
    }
    var left = width; var top = height; var right = -1; var bottom = -1
    pixels.forEachIndexed { index, color ->
        if (color ushr 24 != 0) {
            val x = index % width; val y = index / width
            left = minOf(left, x); right = maxOf(right, x)
            top = minOf(top, y); bottom = maxOf(bottom, y)
        }
    }
    if (right < left) return AtlasCutout(IntArray(1), 1, 1)
    val croppedWidth = right - left + 1
    val croppedHeight = bottom - top + 1
    val cropped = IntArray(croppedWidth * croppedHeight)
    for (y in 0 until croppedHeight) {
        pixels.copyInto(cropped, y * croppedWidth, (top + y) * width + left, (top + y) * width + right + 1)
    }
    return AtlasCutout(cropped, croppedWidth, croppedHeight)
}
