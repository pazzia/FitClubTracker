package com.fitclub.tracker.ui.anatomy

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fitclub.tracker.data.model.Sex
import com.fitclub.tracker.ui.components.FcCard
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Clickable zones of the full-height chart; the index matches the pixel value in `bodymap/<sex>_zones.png`. */
enum class BodyZone(val index: Int, val title: String, val muscleId: String, val color: Long) {
    CHEST(1, "Грудные", "fit-2301", 0xFFF38840),
    DELTS(2, "Дельтовидные", "fit-2452", 0xFFF4D13C),
    BICEPS(3, "Бицепс", "fit-2464", 0xFFD97157),
    TRICEPS(4, "Трицепс", "fit-2471", 0xFFD97157),
    FOREARM(5, "Предплечье", "fit-2496", 0xFFCA4A44),
    ABS(6, "Прямая мышца живота", "fit-2357", 0xFFD94A79),
    OBLIQUES(7, "Косые мышцы живота", "fit-2364", 0xFFE98883),
    TRAPS(8, "Трапеция", "fit-2226", 0xFFCA4A44),
    LATS(9, "Широчайшие", "fit-2231", 0xFF6BA1D2),
    GLUTES(10, "Ягодичные", "fit-2598", 0xFFC87D41),
    QUADS(11, "Квадрицепс", "fit-2613", 0xFF547CC5),
    ADDUCTORS(12, "Приводящие", "fit-2628", 0xFF8254AD),
    HAMSTRINGS(13, "Задняя поверхность бедра", "fit-2638", 0xFF8254AD),
    CALVES(14, "Икры", "fit-2656", 0xFF7DAE41);

    companion object {
        fun of(index: Int) = entries.firstOrNull { it.index == index }
    }
}

/** A decoded zone map: one byte per pixel, 0 — no muscle. */
class ZoneMap(val width: Int, val height: Int, private val cells: ByteArray) {
    fun at(x: Int, y: Int): Int = if (x in 0 until width && y in 0 until height) cells[y * width + x].toInt() and 0xFF else 0

    /** Zone under a tap given as fractions of the picture, or the nearest one within [radius] cells. */
    fun zoneAt(fx: Float, fy: Float, radius: Int = 10): BodyZone? {
        val cx = (fx * width).toInt()
        val cy = (fy * height).toInt()
        BodyZone.of(at(cx, cy))?.let { return it }
        for (r in 1..radius) {
            var best: BodyZone? = null
            for (dy in -r..r) for (dx in -r..r) {
                if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != r) continue
                BodyZone.of(at(cx + dx, cy + dy))?.let { if (best == null) best = it }
            }
            if (best != null) return best
        }
        return null
    }

    companion object {
        fun decode(bytes: ByteArray): ZoneMap? {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inScaled = false }) ?: return null
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return ZoneMap(bitmap.width, bitmap.height, ByteArray(pixels.size) { ((pixels[it] shr 16) and 0xFF).toByte() })
        }
    }
}

private fun sexKey(sex: Sex) = if (sex == Sex.FEMALE) "female" else "male"

/** Full-height anatomical chart: tap a coloured muscle to open its card in the atlas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyAtlasCard(sex: Sex, onMuscle: (String) -> Unit) {
    val context = LocalContext.current.applicationContext
    val key = sexKey(sex)
    val picture by produceState<ImageBitmap?>(null, key) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("bodymap/$key.webp").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    val zones by produceState<ZoneMap?>(null, key) {
        value = withContext(Dispatchers.IO) { runCatching { context.assets.open("bodymap/${key}_zones.png").use { ZoneMap.decode(it.readBytes()) } }.getOrNull() }
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var touched by remember { mutableStateOf<BodyZone?>(null) }
    FcCard(Modifier.fillMaxWidth().testTag("body-atlas")) {
        Text("Атлас тела", style = MaterialTheme.typography.titleMedium, color = Fc.Text)
        Text("Нажмите на мышцу — откроется её карточка в справочнике", style = MaterialTheme.typography.bodySmall, color = Fc.Muted)
        Spacer(Modifier.height(12.dp))
        val image = picture
        Box(
            Modifier.fillMaxWidth().aspectRatio(if (image != null) image.width.toFloat() / image.height else 0.73f)
                .clip(RoundedCornerShape(14.dp)).background(Color.White)
                .onSizeChanged { size = it }
                .semantics { contentDescription = "Ростовой атлас мышц, вид спереди и сзади" }
                .pointerInput(zones, size) {
                    detectTapGestures { offset ->
                        val map = zones ?: return@detectTapGestures
                        if (size.width == 0 || size.height == 0) return@detectTapGestures
                        map.zoneAt(offset.x / size.width, offset.y / size.height)?.let { zone ->
                            touched = zone
                            onMuscle(zone.muscleId)
                        }
                    }
                }
        ) {
            if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BodyZone.entries.forEach { zone ->
                val on = touched == zone
                Row(
                    Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp))
                        .then(if (on) Modifier.background(Fc.LimeSoft) else Modifier.border(1.dp, Fc.Line, RoundedCornerShape(18.dp)))
                        .clickable(role = Role.Button, onClickLabel = "Открыть карточку мышцы") { touched = zone; onMuscle(zone.muscleId) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(zone.color)))
                    Text(zone.title, color = Fc.Text, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Цвета обозначают группы мышц, а не нагрузку. Модель соответствует полу в личном кабинете.",
            style = MaterialTheme.typography.bodySmall, color = Fc.Muted)
    }
}
