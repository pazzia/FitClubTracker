package com.fitclub.tracker.ui.library

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitclub.tracker.data.seed.ExerciseMedia
import com.fitclub.tracker.ui.components.FcCard
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
internal fun ExerciseMediaCard(exerciseId: String, name: String, media: ExerciseMedia) {
    val context = LocalContext.current
    var frame by rememberSaveable(exerciseId) { mutableIntStateOf(0) }
    val path = media.images.getOrNull(frame) ?: media.images.firstOrNull()
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = null
        value = withContext(Dispatchers.IO) {
            try {
                path?.let { context.assets.open(it).use { stream -> BitmapFactory.decodeStream(stream)?.asImageBitmap() } }
            } catch (_: IOException) { null }
        }
    }
    FcCard {
        Text("Техника в фотографиях", color = Fc.Text, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(260.dp).background(Color.White), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it, "$name — фото ${frame + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                ?: Text("Загрузка фотографии…", color = Color.DarkGray)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            media.images.forEachIndexed { index, _ ->
                FilterChip(selected = frame == index, onClick = { frame = index },
                    label = { Text("Фото ${index + 1}") })
            }
        }
        media.videoUrl?.let { url ->
            OutlinedButton(onClick = {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "Не найдено приложение для открытия видео", Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("▶ Видео техники · YouTube") }
            Text("Видео открывается в YouTube или браузере; нужен интернет.",
                color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Text("Фото: Free Exercise DB", color = Fc.Muted, style = MaterialTheme.typography.labelSmall)
    }
}
