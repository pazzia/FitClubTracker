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
        val cutout = trimAtlasBackdrop(pixels, source.width, source.height,
            atlasClothingBands(resources.getResourceEntryName(resource)))
        val result = Bitmap.createBitmap(cutout.pixels, cutout.width, cutout.height, Bitmap.Config.ARGB_8888)
        source.recycle()
        cache.put(resource, result)
        return result
    }
}

internal data class AtlasCutout(val pixels: IntArray, val width: Int, val height: Int)

internal data class AtlasBand(val left: Float, val right: Float, val top: Float, val bottom: Float)

/** Clothing bands belong to the pinned atlas framing, not to the card's crop or theme. */
internal fun atlasClothingBands(name: String): List<AtlasBand> {
    if (!name.startsWith("group_female_")) return emptyList()
    return when (name.removePrefix("group_female_").toIntOrNull()) {
        0 -> listOf(AtlasBand(0f, 1f, .4f, .89f))
        1 -> listOf(AtlasBand(.65f, 1f, .5f, .95f), AtlasBand(.2f, .4f, .7f, .95f))
        2 -> listOf(AtlasBand(.85f, 1f, .55f, .9f))
        3 -> listOf(AtlasBand(0f, .35f, .27f, .75f), AtlasBand(.3f, .87f, .53f, .81f),
            AtlasBand(.65f, .84f, .02f, .6f))
        5 -> listOf(AtlasBand(0f, 1f, .08f, .30f), AtlasBand(0f, 1f, .77f, .92f))
        7 -> listOf(AtlasBand(0f, .8f, .8f, .95f))
        8 -> listOf(AtlasBand(0f, 1f, .04f, .25f))
        9 -> listOf(AtlasBand(0f, 1f, 0f, .18f))
        10 -> listOf(AtlasBand(0f, 1f, 0f, .14f))
        11 -> listOf(AtlasBand(0f, .5f, .7f, 1f), AtlasBand(.5f, 1f, .7f, 1f))
        else -> emptyList()
    }
}

internal fun trimAtlasBackdrop(source: IntArray, width: Int, height: Int,
    clothingBands: List<AtlasBand> = emptyList()): AtlasCutout {
    require(width > 0 && height > 0 && source.size == width * height)
    val pixels = source.copyOf()
    val visited = BooleanArray(pixels.size)
    val protected = BooleanArray(pixels.size)
    fun isBackdrop(color: Int, tolerance: Int): Boolean = color ushr 24 == 0 ||
        (abs(((color ushr 16) and 255) - 20) <= tolerance &&
            abs(((color ushr 8) and 255) - 25) <= tolerance && abs((color and 255) - 29) <= tolerance)
    // Close only clothing spans between actual foreground pixels. Separate shoe bands keep
    // the real background between the legs transparent; no generic rectangular masks are used.
    for (band in clothingBands) {
        val x0 = (band.left * width).toInt().coerceIn(0, width)
        val x1 = kotlin.math.ceil((band.right * width).toDouble()).toInt().coerceIn(0, width)
        val y0 = (band.top * height).toInt().coerceIn(0, height)
        val y1 = kotlin.math.ceil((band.bottom * height).toDouble()).toInt().coerceIn(0, height)
        for (y in y0 until y1) {
            var first = -1; var last = -1
            for (x in x0 until x1) if (!isBackdrop(source[y * width + x], 6)) {
                if (first < 0) first = x
                last = x
            }
            if (first >= 0) for (x in first..last) protected[y * width + x] = true
        }
    }
    val queue = IntArray(pixels.size)
    var tail = 0
    fun enqueue(index: Int) {
        if (visited[index]) return
        visited[index] = true
        val color = pixels[index]
        // The source WebP backdrop is #14191D with small compression variations.
        if (!protected[index] && isBackdrop(color, 18)) {
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
