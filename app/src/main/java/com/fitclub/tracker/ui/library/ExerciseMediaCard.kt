package com.fitclub.tracker.ui.library

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.platform.testTag
import com.fitclub.tracker.ui.components.ExercisePhoto
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitclub.tracker.data.seed.ExerciseMedia
import com.fitclub.tracker.ui.components.FcCard
import com.fitclub.tracker.ui.theme.Fc

@Composable
internal fun ExerciseMediaCard(exerciseId: String, name: String, media: ExerciseMedia) {
    val context = LocalContext.current
    key(exerciseId) {
        val pager = rememberPagerState(pageCount = { media.images.size })
        val scope = rememberCoroutineScope()
        FcCard {
            Text("Техника в фотографиях", color = Fc.Text, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()
                .testTag("exercise-photo-pager"), key = { media.images[it] }) { page ->
                ExercisePhoto(media.images[page], "$name — фото ${page + 1}", Modifier.fillMaxWidth())
            }
            Text("${pager.currentPage + 1} / ${media.images.size} · Листайте свайпом",
                color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
            media.photoNote?.let { Text(it, color = Fc.Muted, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                media.images.forEachIndexed { index, _ ->
                    FilterChip(selected = pager.currentPage == index,
                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
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

        }
    }
}
