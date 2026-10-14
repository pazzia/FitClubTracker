package com.fitclub.tracker.ui.anatomy

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.ui.components.*
import com.fitclub.tracker.ui.library.LibraryViewModel
import com.fitclub.tracker.ui.navigation.appViewModel
import com.fitclub.tracker.ui.theme.Condensed
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Colours of the illustration plates: they are drawn on warm paper in both themes. */
private val Paper = Color(0xFFF5F0E6)
private val Terracotta = Color(0xFFD9785A)
private val Bone = Color(0xFFA8A39A)

private const val DISCLAIMER = "Анатомия проверена ИИ по справочникам, экспертная приёмка не проводилась. " +
    "Подбор упражнений — черновик, требует проверки тренером."

@Composable
private fun rememberAtlas(): State<Result<MuscleAtlas>?> {
    val context = LocalContext.current.applicationContext
    return produceState<Result<MuscleAtlas>?>(null, context) {
        value = withContext(Dispatchers.IO) { runCatching { MuscleAtlas.load(context) } }
    }
}

@Composable
private fun AssetImage(path: String, description: String?, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    val image = bitmap
    if (image != null) Image(image, description, modifier, contentScale = contentScale)
    // While the illustration loads, a neutral surface instead of a bright paper flash in the dark theme.
    else Box(modifier.background(Fc.Surface2))
}

@Composable
private fun AtlasState(result: Result<*>?) {
    when {
        result == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Fc.Lime, strokeWidth = 2.dp)
            Text("Загрузка атласа…", color = Fc.Muted)
        }
        result.isFailure -> Text("Не удалось открыть атлас. Вернитесь в справочник и откройте его снова.", color = Fc.Text)
    }
}

@Composable
private fun FitnessPill() {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(Fc.LimeSoft).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LineIcon(DUMBBELL, Fc.Lime, Modifier.size(14.dp), strokeWidth = 2.4f)
        Text("Для фитнеса", color = Fc.Lime, style = MaterialTheme.typography.labelMedium)
    }
}

private const val DUMBBELL = "M6 7v10M18 7v10M3 10v4M21 10v4M6 12h12"

// ---------------------------------------------------------------- Atlas home

@Composable
fun AtlasHomeScreen(onBack: () -> Unit, onGroup: (String) -> Unit, onMuscle: (String) -> Unit) {
    val result by rememberAtlas()
    val atlas = result?.getOrNull()
    var query by rememberSaveable { mutableStateOf("") }
    val found = remember(atlas, query) { atlas?.search(query).orEmpty() }
    LazyColumn(
        Modifier.fillMaxSize().testTag("atlas-home-list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { BackRow("Справочник", onBack) }
        item {
            ScreenTitle("Атлас мышц", Modifier.semantics { heading() })
            if (atlas != null) Text("${musclesWord(atlas.muscles.size)} · ${atlas.groups.size} групп", color = Fc.Muted)
        }
        item {
            OutlinedTextField(query, { query = it },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Поиск мышцы" },
                placeholder = { Text("Мышца по-русски или на латыни", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Fc.Muted) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Fc.Surface, unfocusedContainerColor = Fc.Surface,
                    focusedBorderColor = Fc.Lime, unfocusedBorderColor = Fc.Line, focusedTextColor = Fc.Text, unfocusedTextColor = Fc.Text))
        }
        if (atlas == null) item { AtlasState(result) }
        else if (query.isNotBlank()) {
            item { Eyebrow(if (found.isEmpty()) "Ничего не найдено" else "Найдено: ${found.size}") }
            items(found, key = { it.id }) { m -> MuscleRow(m, atlas.group(m.groupId)?.title, onClick = { onMuscle(m.id) }) }
        } else {
            item { Eyebrow("Мышечные группы") }
            items(atlas.groups.chunked(2), key = { row -> row.first().id }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { g -> GroupTile(g, Modifier.weight(1f)) { onGroup(g.id) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item { Text("Подборка мышц силовых движений и стабилизаторов. $DISCLAIMER", color = Fc.Muted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun GroupTile(group: AtlasGroup, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(Fc.Surface).border(1.dp, Fc.Line2, shape)
            .clickable(role = Role.Button, onClickLabel = "Открыть группу ${group.title}", onClick = onClick)
    ) {
        AssetImage((group.muscles.firstOrNull { it.hasIllustration } ?: group.muscles.first()).thumbnail, null, Modifier.fillMaxWidth().aspectRatio(1.35f))
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(group.title, color = Fc.Text, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(musclesWord(group.muscles.size), color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MuscleRow(m: AtlasMuscle, subtitle: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Fc.Surface).border(1.dp, Fc.Line2, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (m.hasIllustration) AssetImage(m.thumbnail, null, Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)))
        else Box(Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)).background(Paper), contentAlignment = Alignment.Center) {
            Text("схема", color = Color(0xFF8A5A44), style = MaterialTheme.typography.labelMedium)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(m.name, color = Fc.Text, style = MaterialTheme.typography.titleSmall)
            Text(m.latin, color = Fc.Muted, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
            val line = subtitle ?: m.fitness
            if (line != null) Text(line, color = Fc.Text.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
        LineIcon(LineIcons.NEXT, Fc.Dim, Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------- Group

@Composable
fun AtlasGroupScreen(groupId: String, onBack: () -> Unit, onMuscle: (String) -> Unit) {
    val result by rememberAtlas()
    val group = result?.getOrNull()?.group(groupId)
    LazyColumn(
        Modifier.fillMaxSize().testTag("atlas-group-list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { BackRow("Атлас мышц", onBack) }
        if (group == null) {
            item { if (result?.isSuccess == true) Text("Группа не найдена", color = Fc.Muted) else AtlasState(result) }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScreenTitle(group.title, Modifier.semantics { heading() })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FitnessPill()
                        Text(musclesWord(group.muscles.size), color = Fc.Muted)
                    }
                }
            }
            items(group.muscles, key = { it.id }) { m -> MuscleRow(m, null) { onMuscle(m.id) } }
        }
    }
}

// ---------------------------------------------------------------- Muscle card

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AtlasMuscleScreen(muscleId: String, onBack: () -> Unit, onMuscle: (String) -> Unit, onExercise: (String) -> Unit) {
    val result by rememberAtlas()
    val atlas = result?.getOrNull()
    val muscle = atlas?.muscle(muscleId)
    val vm = appViewModel { LibraryViewModel(it) }
    val catalog by vm.exercises.collectAsStateWithLifecycle()
    val byId = remember(catalog) { catalog.associateBy { it.id } }
    var part by rememberSaveable(muscleId) { mutableStateOf<String?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()

    LazyColumn(
        Modifier.fillMaxSize().testTag("atlas-muscle-list"), state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (atlas == null || muscle == null) {
            item { BackRow("Атлас мышц", onBack) }
            item { if (result?.isSuccess == true) Text("Мышца не найдена", color = Fc.Muted) else AtlasState(result) }
            return@LazyColumn
        }
        val group = atlas.group(muscle.groupId)
        val linked = muscle.exercises.filter { it.exerciseId in byId }
        val filters = partFilters(linked)
        val shown = linked.filter { part == null || it.hint?.let(::hintLabel) == part }

        item { BackRow(group?.title ?: "Атлас мышц", onBack) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(muscle.name, color = Fc.Text, modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Condensed, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp))
                Text(muscle.latin, color = Fc.Muted, fontStyle = FontStyle.Italic)
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FitnessPill()
                    muscle.role?.let { Pill(it, Fc.Surface2, Fc.Text) }
                    if (linked.isNotEmpty()) {
                        Text(exercisesWord(linked.size) + " ↓", color = Fc.Lime, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Fc.Surface2)
                                .clickable(role = Role.Button) { scope.launch { listState.animateScrollToItem(EXERCISES_INDEX) } }
                                .padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
        }
        item {
            if (muscle.hasIllustration) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AssetImage(muscle.image, "${muscle.name}: выбранная мышца выделена терракотовым цветом",
                    Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(20.dp)).background(Paper), ContentScale.Fit)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Legend(Terracotta, "Выбранная мышца")
                    Legend(Bone, "Окружающие структуры")
                }
            } else SchematicCard(muscle.schematic)
        }
        item {
            FcCard(Modifier.fillMaxWidth()) {
                val rows = listOfNotNull(
                    muscle.where?.let { "Где находится" to it }, muscle.does?.let { "Что делает" to it },
                    muscle.fitness?.let { "В фитнесе" to it }, muscle.details?.let { "Особенности" to it })
                rows.forEachIndexed { i, (title, text) ->
                    if (i > 0) HorizontalDivider(color = Fc.Line2, modifier = Modifier.padding(vertical = 14.dp))
                    Eyebrow(title, color = Fc.Lime)
                    Spacer(Modifier.height(6.dp))
                    Text(text, color = Fc.Text, style = MaterialTheme.typography.bodyLarge)
                }
                listOf("Части мышцы" to muscle.parts, "Состав" to muscle.members).forEach { (title, names) ->
                    if (names.isEmpty()) return@forEach
                    HorizontalDivider(color = Fc.Line2, modifier = Modifier.padding(vertical = 14.dp))
                    Eyebrow(title, color = Fc.Lime)
                    names.forEach { n ->
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(n.name, color = Fc.Text, style = MaterialTheme.typography.titleSmall)
                            if (n.latin.isNotBlank()) Text(n.latin, color = Fc.Muted, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                        }
                    }
                }
            }
        }
        // index EXERCISES_INDEX: keep the items above in sync with this constant
        item(key = "exercises") {
            Column(Modifier.testTag("atlas-exercises"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Упражнения", color = Fc.Text, modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Medium))
                muscle.trainingNote?.let { note ->
                    Text(note, color = Fc.Text, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Fc.EmberSoft)
                            .border(1.dp, Fc.Ember.copy(alpha = .4f), RoundedCornerShape(16.dp)).padding(14.dp))
                }
                if (filters.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FcChip("Все", part == null, { part = null }) }
                        items(filters) { f -> FcChip(f, part == f, { part = if (part == f) null else f }) }
                    }
                }
                if (linked.isEmpty() && muscle.trainingNote == null) {
                    Text("Упражнения для этой мышцы ещё не подобраны.", color = Fc.Muted)
                }
            }
        }
        items(shown, key = { "ex:" + it.exerciseId }) { link ->
            ExerciseRow(byId.getValue(link.exerciseId), link) { onExercise(link.exerciseId) }
        }
        item {
            val (prev, next) = atlas.neighbours(muscle.id)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeighbourCard("Предыдущая", prev, Modifier.weight(1f), TextAlign.Start, onMuscle)
                NeighbourCard("Следующая", next, Modifier.weight(1f), TextAlign.End, onMuscle)
            }
        }
        item { Text(DISCLAIMER, color = Fc.Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

/** Position of the «Упражнения» header: back row, title, illustration, facts. */
private const val EXERCISES_INDEX = 4

/** Origin and insertion of each part, drawn natively so the text stays sharp on any screen. */
@Composable
private fun SchematicCard(rows: List<AtlasAttachment>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Paper).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Схема начала и прикрепления", color = Color(0xFF5B4A3A), style = MaterialTheme.typography.labelMedium)
        rows.forEach { r ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.part, color = Color(0xFFA4472A), style = MaterialTheme.typography.titleSmall)
                Text("Начало", color = Color(0xFF7A6A5A), style = MaterialTheme.typography.labelSmall)
                Text(r.origin, color = Color(0xFF2B2420), style = MaterialTheme.typography.bodyMedium)
                Text("↓ Прикрепление", color = Color(0xFF7A6A5A), style = MaterialTheme.typography.labelSmall)
                Text(r.insertion, color = Color(0xFF2B2420), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("Условная схема связей: не показывает форму, масштаб и положение мышцы.", color = Color(0xFF7A6A5A), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(text, color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ExerciseRow(exercise: ExerciseEntity, link: AtlasExercise, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).clip(shape).background(Fc.Surface).border(1.dp, Fc.Line2, shape)
            .clickable(role = Role.Button, onClickLabel = "Открыть упражнение", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Fc.Surface2), contentAlignment = Alignment.Center) {
            LineIcon(DUMBBELL, Fc.Lime, Modifier.size(22.dp), strokeWidth = 1.8f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(exercise.name, color = Fc.Text, style = MaterialTheme.typography.titleSmall)
            Text(listOfNotNull(exercise.equipment.takeIf { it.isNotBlank() }, link.hint).joinToString(" · "),
                color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (link.main) Pill("основное", Fc.LimeSoft, Fc.Lime)
        else Text("помогает", color = Fc.Muted, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, Fc.Line, RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
private fun NeighbourCard(label: String, muscle: AtlasMuscle?, modifier: Modifier, align: TextAlign, onMuscle: (String) -> Unit) {
    if (muscle == null) { Spacer(modifier); return }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(Fc.Surface).border(1.dp, Fc.Line2, shape)
            .clickable(role = Role.Button) { onMuscle(muscle.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = if (align == TextAlign.End) Alignment.End else Alignment.Start
    ) {
        Text(label, color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
        Text(muscle.name, color = Fc.Text, style = MaterialTheme.typography.titleSmall, textAlign = align)
    }
}
