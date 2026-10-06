package com.fitclub.tracker.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import com.fitclub.tracker.ui.theme.Fc
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.seed.FreeExerciseCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private val photoCache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

@Composable
fun ExerciseThumbnail(exercise: ExerciseEntity, modifier: Modifier = Modifier) {
    ExercisePhoto(FreeExerciseCatalog.media[exercise.id]?.images?.let { it.getOrNull(1) ?: it.firstOrNull() },
        "${exercise.name} — миниатюра техники", modifier.clip(RoundedCornerShape(11.dp)), thumbnail = true)
}

/** Sample list images separately from detail photos; cache only a bounded amount of decoded pixels. */
@Composable
fun ExercisePhoto(path: String?, description: String, modifier: Modifier = Modifier, thumbnail: Boolean = false) {
    val context = LocalContext.current
    val aspect = remember(context, path, thumbnail) {
        if (thumbnail || path == null) 1.5f else runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth.toFloat() / bounds.outHeight else 1.5f
        }.getOrDefault(1.5f)
    }
    val cacheKey = "$path:$thumbnail"
    val photo by produceState<Pair<Boolean, Bitmap?>>(true to photoCache.get(cacheKey), cacheKey) {
        val decoded = withContext(Dispatchers.IO) {
            photoCache.get(cacheKey) ?: try {
                path?.let {
                    val options = BitmapFactory.Options().apply { inSampleSize = if (thumbnail) 2 else 1 }
                    if (thumbnail) {
                        val preparedPath = "exercise_thumbnails/" + it.removePrefix("free_exercise_db/")
                        val prepared = try {
                            context.assets.open(preparedPath).use { stream -> BitmapFactory.decodeStream(stream, null, options) }
                        } catch (_: IOException) { null }
                        if (prepared != null) {
                            val maskPath = "exercise_masks/" + it.removePrefix("free_exercise_db/").removeSuffix(".jpg") + ".png"
                            val mask = context.assets.open(maskPath).use { stream -> BitmapFactory.decodeStream(stream) }
                            val result = if (mask != null && mask.width / options.inSampleSize == prepared.width && mask.height / options.inSampleSize == prepared.height) frameExerciseThumbnail(prepared, mask) else trimPhotoPadding(prepared)
                            mask?.recycle()
                            if (result !== prepared) prepared.recycle()
                            photoCache.put(cacheKey, result)
                            return@let result
                        }
                    }
                    context.assets.open(it).use { stream -> BitmapFactory.decodeStream(stream, null, options) }
                        ?.let { decoded ->
                            if (!thumbnail) {
                                photoCache.put(cacheKey, decoded)
                                return@let decoded
                            }
                            val maskPath = "exercise_masks/" + it.removePrefix("free_exercise_db/").removeSuffix(".jpg") + ".png"
                            val mask = context.assets.open(maskPath).use { stream -> BitmapFactory.decodeStream(stream) }
                                ?: throw IOException("Missing foreground matte: $maskPath")
                            // Process before trimming so matte coordinates remain aligned at both sample sizes.
                            val processed = renderExercisePhoto(decoded, mask)
                            val result = frameExerciseThumbnail(processed, mask)
                            mask.recycle()
                            decoded.recycle()
                            if (result !== processed) processed.recycle()
                            photoCache.put(cacheKey, result)
                            result
                        }
                }
            } catch (_: IOException) { null }
        }
        value = false to decoded
    }
    val loading = photo.first
    val bitmap = photo.second
    val layout = if (thumbnail) modifier else modifier.fillMaxWidth().aspectRatio(aspect)
    Box(layout.background(Fc.Surface2).semantics { contentDescription = description },
        contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } ?: Text(if (loading) "Загрузка…" else "Фото недоступно", color = Fc.Muted)
    }
}

