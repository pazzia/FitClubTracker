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
    val cacheKey = "$path:$thumbnail"
    val bitmap by produceState<Bitmap?>(photoCache.get(cacheKey), cacheKey) {
        value = photoCache.get(cacheKey)
        value = withContext(Dispatchers.IO) {
            photoCache.get(cacheKey) ?: try {
                path?.let {
                    val options = BitmapFactory.Options().apply { inSampleSize = if (thumbnail) 2 else 1 }
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
                            mask.recycle()
                            val result = trimPhotoPadding(processed, decoded)
                            decoded.recycle()
                            if (result !== processed) processed.recycle()
                            photoCache.put(cacheKey, result)
                            result
                        }
                }
            } catch (_: IOException) { null }
        }
    }
    val layout = if (thumbnail) modifier else modifier.fillMaxWidth().aspectRatio(bitmap?.let { it.width.toFloat() / it.height } ?: 1.5f)
    Box(layout.background(Fc.Surface2).semantics { contentDescription = description },
        contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } ?: Text(if (thumbnail) "…" else "Загрузка фотографии…", color = Fc.Muted)
    }
}
