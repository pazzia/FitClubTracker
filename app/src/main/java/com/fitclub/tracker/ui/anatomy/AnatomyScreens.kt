package com.fitclub.tracker.ui.anatomy

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fitclub.tracker.ui.components.*
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun atlasData(): State<Result<List<AtlasMuscle>>?> {
    val context = LocalContext.current.applicationContext
    return produceState<Result<List<AtlasMuscle>>?>(null, context) {
        value = withContext(Dispatchers.IO) { runCatching { AnatomyCatalog.load(context) } }
    }
}

@Composable
fun AnatomyCatalogScreen(onBack: () -> Unit, onMuscle: (String) -> Unit) {
    val result by atlasData()
    var query by rememberSaveable { mutableStateOf("") }
    var fitness by rememberSaveable { mutableStateOf(false) }
    var role by rememberSaveable { mutableStateOf<String?>(null) }
    var region by rememberSaveable { mutableStateOf<String?>(null) }
    var grouped by rememberSaveable { mutableStateOf(true) }
    var expanded by rememberSaveable { mutableStateOf(listOf("Плечо")) }
    val all = result?.getOrNull().orEmpty()
    val shown = remember(all, query, fitness, role, region) { AnatomyCatalog.filter(all, query, fitness, role, region) }
    LazyColumn(Modifier.fillMaxSize().testTag("anatomy-catalog-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BackRow("Справочник", onBack) }
        item { ScreenTitle("Анатомия мышц"); Text("Строение · функции · участие в фитнесе", color = Fc.Muted) }
        item {
            OutlinedTextField(query, { query = it }, label = { Text("Поиск мышцы") },
                placeholder = { Text("Бицепс, двуглавая, biceps…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Switch) { fitness = !fitness; if (!fitness) role = null }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Только для фитнеса", Modifier.weight(1f), color = Fc.Text)
                Switch(fitness, { fitness = it; if (!it) role = null })
            }
            Text("Мышцы силовых движений и стабилизаторы. Это подборка, а не анатомический тип.", style = MaterialTheme.typography.bodySmall, color = Fc.Muted)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FcChip("Все роли", role == null, { role = null }) }
                items(AnatomyCatalog.roles.entries.filter { it.key != "outside" }) { entry ->
                    FcChip(entry.value, role == entry.key, { role = if (role == entry.key) null else entry.key; if (role != null) fitness = true })
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FcChip("Все области", region == null, { region = null }) }
                items(all.map { it.region }.distinct().sorted()) { r -> FcChip(r, region == r, { region = if (region == r) null else r }) }
            }
        }
        item {
            Segmented(listOf(true, false), grouped, { grouped = it }, { if (it) "По областям" else "Списком" }, selectedBg = Fc.Lime)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${shown.size} из ${all.size} записей", Modifier.weight(1f), color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { query = ""; fitness = false; role = null; region = null }) { Text("Сбросить") }
            }
        }
        if (result == null) item { CircularProgressIndicator(); Text("Загрузка атласа…", color = Fc.Muted) }
        else if (result!!.isFailure) item { Text("Не удалось прочитать атлас. Вернитесь в справочник и откройте его снова.", color = Fc.Text) }
        else if (shown.isEmpty()) item { Text("Ничего не найдено. Измените запрос или сбросьте фильтры.", color = Fc.Muted) }
        if (grouped && query.isBlank()) {
            shown.groupBy { it.region }.toSortedMap().forEach { (r, records) ->
                item(key = "region:$r") {
                    TextButton(onClick = { expanded = if (r in expanded) expanded - r else expanded + r }, modifier = Modifier.fillMaxWidth()) {
                        Text("${if (r in expanded) "−" else "+"} $r · ${records.size}", Modifier.fillMaxWidth(), color = Fc.Text)
                    }
                }
                if (r in expanded) items(records, key = { it.id }) { AtlasRow(it) { onMuscle(it.id) } }
            }
        } else items(shown, key = { it.id }) { AtlasRow(it) { onMuscle(it.id) } }
        item { Text("Включены все 274 записи БД 0.1: мышцы, комплексы, серии и компоненты. Описания и рисунки заполнены частично и отмечены в карточках.", color = Fc.Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun AtlasRow(m: AtlasMuscle, onClick: () -> Unit) {
    FcCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Text(m.name, color = Fc.Text, style = MaterialTheme.typography.titleMedium)
        Text(m.latin, color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text(if (m.fitness) "Для фитнеса · ${AnatomyCatalog.roles[m.role]}" else "Общий атлас", color = Fc.Lime, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun AnatomyDetailScreen(id: String, onBack: () -> Unit, onMuscle: (String) -> Unit, onBicepsExercises: () -> Unit = {}) {
    val result by atlasData()
    val all = result?.getOrNull().orEmpty()
    val muscle = all.firstOrNull { it.id == id }
    var tab by rememberSaveable(id) { mutableIntStateOf(0) }
    var board by rememberSaveable(id) { mutableIntStateOf(0) }
    val uriHandler = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize().testTag("anatomy-detail-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { BackRow("Анатомия мышц", onBack) }
        if (muscle == null) {
            item { Text(if (result == null) "Загрузка…" else "Запись не найдена", color = Fc.Muted) }
        } else {
            item {
                Eyebrow(muscle.region)
                ScreenTitle(muscle.name)
                Text(muscle.latin, color = Fc.Muted)
                Spacer(Modifier.height(10.dp))
                Pill(if (muscle.fitness) "Для фитнеса" else "Общий атлас", Fc.LimeSoft, Fc.Lime)
            }
            item { Segmented(listOf(0, 1, 2), tab, { tab = it }, { listOf("Строение", "Функции", "В фитнесе")[it] }, selectedBg = Fc.Lime) }
            when (tab) {
                0 -> {
                    item { AtlasField("Расположение", muscle.layer) }
                    item { AtlasField("Начало мышцы", muscle.origin) }
                    item { AtlasField("Прикрепление", muscle.insertion) }
                    if (muscle.assets.isNotEmpty()) {
                        item {
                            Text("Иллюстрации из базы", color = Fc.Text, style = MaterialTheme.typography.titleMedium)
                            Text("Черновые доски могут содержать несколько мышц. Рисунки требуют анатомической проверки.", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (muscle.assets.size > 1) item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(muscle.assets.size) { i -> FcChip("Доска ${i + 1}", board == i, { board = i }) }
                            }
                        }
                        item { AtlasBoard(muscle.assets[board.coerceIn(muscle.assets.indices)]) }
                    } else item { Text("Иллюстрация для этой записи ещё не добавлена в БД.", color = Fc.Muted) }
                }
                1 -> item { AtlasField("За что отвечает", muscle.action) }
                2 -> {
                    if (muscle.id == "TA2:2464") item { PrimaryButton("Упражнения на бицепс", onBicepsExercises) }
                    item { AtlasField("Роль", AnatomyCatalog.roles[muscle.role]) }
                    item { AtlasField("Почему включена в подборку", muscle.fitnessReason) }
                    item { Text("Отсутствие отметки «Для фитнеса» не означает, что мышца не участвует в физической активности. Отметка не является рекомендацией тренировать мышцу изолированно.", color = Fc.Muted, style = MaterialTheme.typography.bodySmall) }
                }
            }
            val related = all.filter { it.id in muscle.relatedIds }
            if (related.isNotEmpty()) {
                item { Text("Связанные структуры", color = Fc.Text, style = MaterialTheme.typography.titleMedium) }
                items(related, key = { it.id }) { AtlasRow(it) { onMuscle(it.id) } }
            }
            item {
                HorizontalDivider(color = Fc.Line)
                Text("${muscle.id} · База 0.1", color = Fc.Muted, modifier = Modifier.padding(top = 12.dp))
                Text(if (muscle.contentStatus == "source_checked_not_expert_reviewed") "Текст сверен с источником. Экспертная проверка карточки ещё не завершена." else "Данные заполнены частично. Отсутствующие сведения не заменены предположениями.", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
            }
            items(muscle.sourceUrls) { url -> TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text("Открыть источник: ${android.net.Uri.parse(url).host.orEmpty()}") } }
        }
    }
}

@Composable
private fun AtlasField(title: String, value: String?) {
    FcCard(Modifier.fillMaxWidth()) {
        Text(title, color = Fc.Text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(value ?: "Сведения ещё не заполнены в базе.", color = if (value == null) Fc.Muted else Fc.Text)
    }
}

@Composable
private fun AtlasBoard(assetId: String) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<ImageBitmap?>(null, assetId) {
        value = withContext(Dispatchers.IO) { runCatching {
            context.assets.open("anatomy/$assetId.webp").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull() }
    }
    bitmap?.let { image ->
        Image(image, "Черновая анатомическая доска из базы", Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height), contentScale = ContentScale.Fit)
    } ?: Text("Загрузка иллюстрации…", color = Fc.Muted)
}
