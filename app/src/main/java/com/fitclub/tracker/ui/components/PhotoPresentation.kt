package com.fitclub.tracker.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/** Drop only continuous, almost-white padding touching an image edge, never interior highlights. */
internal fun trimPhotoPadding(source: Bitmap): Bitmap {
    val w = source.width; val h = source.height
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)
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

private val segmentationLock = Mutex()
private val segmenter by lazy {
    Segmentation.getClient(SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build())
}

/** The model is bundled with the APK. No photo or model is uploaded or downloaded by the app. */
internal suspend fun emphasizeExerciseThumbnail(source: Bitmap): Bitmap {
    val mask: FloatArray? = try {
        segmentationLock.withLock {
            withTimeoutOrNull(2_000) {
                suspendCancellableCoroutine<FloatArray?> { continuation ->
                    segmenter.process(InputImage.fromBitmap(source, 0))
                        .addOnSuccessListener { result ->
                            val values = FloatArray(result.width * result.height)
                            result.buffer.rewind(); result.buffer.asFloatBuffer().get(values)
                            if (continuation.isActive) continuation.resume(values)
                        }
                        .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
                }
            }
        }
    } catch (e: CancellationException) { throw e }
      catch (_: Exception) { null }
      catch (_: LinkageError) { null }
    return renderExerciseThumbnail(source, mask)
}

/** Preserve the equipment around the person. Fade background rather than remove uncertain objects. */
internal fun renderExerciseThumbnail(source: Bitmap, confidence: FloatArray?): Bitmap {
    val w = source.width; val h = source.height
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)
    val mask = confidence?.takeIf { it.size == pixels.size }
    var left = w; var right = 0; var top = h; var bottom = 0
    mask?.forEachIndexed { i, value -> if (value > .75f) {
        val x = i % w; val y = i / w
        left = min(left, x); right = max(right, x); top = min(top, y); bottom = max(bottom, y)
    } }
    val hasSubject = left < right && top < bottom
    // Keep a generous envelope for bars, dumbbells, benches and handles held by the athlete.
    val equipment = if (hasSubject) Rect(
        (left - (right - left) * .45f).toInt().coerceAtLeast(0),
        (top - (bottom - top) * .12f).toInt().coerceAtLeast(0),
        (right + (right - left) * .45f).toInt().coerceAtMost(w),
        (bottom + (bottom - top) * .12f).toInt().coerceAtMost(h)) else null
    for (i in pixels.indices) {
        val c = pixels[i]; val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        val paleBackground = r > 238 && g > 238 && b > 238
        val subject = if (hasSubject) max(mask!![i], if (equipment!!.contains(i % w, i / w)) .88f else 0f) else 1f
        val opacity = if (paleBackground && subject < .8f) .06f else if (paleBackground && mask == null) .06f else .22f + .78f * subject
        val contrast = if (subject > .7f) 1.16f else .82f
        val gray = r * .299f + g * .587f + b * .114f
        val saturation = if (subject > .7f) 1.05f else .28f
        fun channel(value: Int) = (((gray + (value - gray) * saturation) - 128) * contrast + 128).toInt().coerceIn(0, 255)
        pixels[i] = Color.argb((opacity * 255).toInt().coerceIn(0, 255), channel(r), channel(g), channel(b))
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}
