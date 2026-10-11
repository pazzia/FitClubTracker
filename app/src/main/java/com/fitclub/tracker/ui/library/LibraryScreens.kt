package com.fitclub.tracker.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitclub.tracker.AppContainer
import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.model.*
import com.fitclub.tracker.data.seed.FreeExerciseCatalog
import com.fitclub.tracker.ui.components.*
import com.fitclub.tracker.ui.navigation.appViewModel
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.flow.*

class LibraryViewModel(c: AppContainer) : ViewModel() {
    val exercises = c.exercises.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = c.profile.settings
    /** Exercises from recent workouts, newest first. */
    val recent = c.fitness.observeWorkouts().map { logs ->
        logs.sortedByDescending { it.session.startedAt }.flatMap { log -> log.sets.filter { it.done }.map { it.exerciseId } }.distinct().take(8)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Equipment filters that match how people search in a gym. */
private val EquipmentFilters = listOf("Тренажёр", "Штанга", "Гантели", "Блок", "Свой вес")

private fun categoryOf(e: ExerciseEntity) = FreeExerciseCatalog.media[e.id]?.category

@Composable
fun LibraryHomeScreen(onGroup: (MuscleGroup) -> Unit, onExercise: (String) -> Unit, onProfile: () -> Unit = {}, onAnatomy: () -> Unit = {},
    onAtlasMuscle: (String) -> Unit = {}) {
    val vm = appViewModel { LibraryViewModel(it) }
    val all by vm.exercises.collectAsStateWithLifecycle()
    val profile by vm.settings.collectAsStateWithLifecycle()
    val recentIds by vm.recent.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var equipment by rememberSaveable { mutableStateOf<String?>(null) }
    val q = query.trim().lowercase()
    val filtering = q.isNotEmpty() || equipment != null
    val results = all.filter { matchesExercise(it, query) && (equipment == null || it.equipment == equipment) }
    val byId = all.associateBy { it.id }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    ScreenTitle("Справочник")
                    Text("${all.size} упражнений в ${AnatomyGroupOrder.size} группах мышц", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                }
                CircleIconButton("Профиль", onProfile, filled = true) { Icon(Icons.Filled.Person, null, tint = Fc.Text) }
            }
        }
        item {
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Поиск упражнений" },
                placeholder = { Text("Упражнение, мышца или тренажёр", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Fc.Muted) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Fc.Surface, unfocusedContainerColor = Fc.Surface,
                    focusedBorderColor = Fc.Lime, unfocusedBorderColor = Fc.Line, focusedTextColor = Fc.Text, unfocusedTextColor = Fc.Text))
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                item { FcChip("Все", equipment == null, { equipment = null }) }
                items(EquipmentFilters) { name ->
                    FcChip(if (name == "Тренажёр") "Тренажёры" else name, equipment == name, { equipment = if (equipment == name) null else name },
                        trailing = "${all.count { it.equipment == name }}")
                }
            }
        }
        // The body comes first: tapping a muscle is the quickest way in for most people.
        if (!filtering) {
            item { com.fitclub.tracker.ui.anatomy.BodyAtlasCard(profile.sex, onAtlasMuscle) }
            item {
                FcCard(Modifier.fillMaxWidth(), onClick = onAnatomy) {
                    Text("Атлас мышц", color = Fc.Text, style = MaterialTheme.typography.titleLarge)
                    Text("120 мышц в 12 группах, упражнения для каждой", color = Fc.Muted)
                    Spacer(Modifier.height(8.dp))
                    Text("Открыть атлас", color = Fc.Lime, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (filtering) {
            item { Eyebrow(if (results.isEmpty()) "Ничего не найдено" else "Найдено: ${results.size}") }
            items(results, key = { it.id }) { e -> ExerciseRow(e, onClick = { onExercise(e.id) }) }
        } else {
            val recent = recentIds.mapNotNull { byId[it] }
            if (recent.isNotEmpty()) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Недавние", Modifier.weight(1f))
                    Text("из ваших тренировок", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recent, key = { it.id }) { e ->
                        Column(Modifier.width(148.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { onExercise(e.id) },
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExerciseThumbnail(e, Modifier.width(148.dp).height(98.dp))
                            Text(e.name, color = Fc.Text, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Группы мышц", style = MaterialTheme.typography.titleMedium, color = Fc.Text, modifier = Modifier.weight(1f))
                    Text("${AnatomyGroupOrder.size} групп", style = MaterialTheme.typography.bodySmall, color = Fc.Muted)
                }
            }
            val counts = all.groupingBy { it.group }.eachCount()
            items(AnatomyGroupOrder.chunked(2)) { pair ->
                MuscleCategoryRow(pair, profile.sex, counts, onGroup)
            }
        }
    }
}

@Composable
private fun ExerciseRow(e: ExerciseEntity, onClick: () -> Unit, onAdd: (() -> Unit)? = null, inProgram: Boolean = false) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Fc.Surface).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExerciseThumbnail(e, Modifier.width(104.dp).height(72.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(e.name, style = MaterialTheme.typography.titleSmall, color = Fc.Text)
                val category = categoryOf(e)
                Text("${e.equipment} · ${if (category == "Растяжка") category.lowercase() else e.type.title.lowercase()} · ${Levels.title(e.level).lowercase()}",
                    style = MaterialTheme.typography.bodySmall, color = Fc.Muted)
                if (inProgram) Pill("В программе", Fc.LimeSoft, Fc.Lime)
            }
        }
        if (onAdd != null) CircleIconButton("Добавить «${e.name}» в программу", onAdd) { LineIcon(LineIcons.PLUS, Fc.Text, Modifier.size(18.dp), 2.4f) }
        else LineIcon(LineIcons.NEXT, Fc.Dim, Modifier.size(18.dp))
    }
}

@Composable
fun LibraryGroupScreen(group: MuscleGroup, onBack: () -> Unit, onExercise: (String) -> Unit, onAdd: (String) -> Unit = {}) {
    val vm = appViewModel { LibraryViewModel(it) }
    val all by vm.exercises.collectAsStateWithLifecycle()
    val profile by vm.settings.collectAsStateWithLifecycle()
    var type by rememberSaveable { mutableStateOf<ExerciseType?>(null) }
    var level by rememberSaveable { mutableStateOf<Int?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var levelMenu by remember { mutableStateOf(false) }
    val inGroup = all.filter { it.group == group }
    fun typeMatches(e: ExerciseEntity, t: ExerciseType?) = t == null || (e.type == t && categoryOf(e) != "Растяжка")
    val shown = inGroup.filter { typeMatches(it, type) && (level == null || it.level == level) && matchesExercise(it, query) }
    LazyColumn(Modifier.fillMaxSize().testTag("library-group-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BackRow("Справочник", onBack) }
        item {
            Row(Modifier.fillMaxWidth().height(176.dp).clip(RoundedCornerShape(24.dp)).background(Fc.Surface), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f).fillMaxHeight().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Eyebrow("Группа мышц")
                    ScreenTitle(group.title)
                    Text("${inGroup.size} упражнений", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    Pill(if (group == MuscleGroup.QUADS) "4 головки" else "${group.regions.size} зон", Fc.LimeSoft, Fc.Lime)
                }
                GroupImage(profile.sex, group, Modifier.weight(0.8f).height(160.dp), scale = ContentScale.Fit, alignment = Alignment.BottomCenter)
            }
        }
        if (group == MuscleGroup.QUADS) item { QuadricepsLegend() }
        item {
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Поиск в группе" },
                placeholder = { Text("Поиск в группе…") }, leadingIcon = { Icon(Icons.Filled.Search, null, tint = Fc.Muted) },
                singleLine = true, shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Fc.Surface, unfocusedContainerColor = Fc.Surface,
                    focusedBorderColor = Fc.Lime, unfocusedBorderColor = Fc.Line, focusedTextColor = Fc.Text, unfocusedTextColor = Fc.Text))
            if (query.isNotBlank()) Text("Найдено: ${shown.size}", color = Fc.Muted, modifier = Modifier.padding(top = 6.dp))
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf<ExerciseType?>(null, ExerciseType.BASIC, ExerciseType.ISOLATION)) { t ->
                        FcChip(when (t) { null -> "Все"; ExerciseType.BASIC -> "Базовые"; else -> "Изоляция" }, type == t, { type = t },
                            trailing = "${inGroup.count { typeMatches(it, t) }}")
                    }
                }
                Box {
                    CircleIconButton(if (level == null) "Фильтр по уровню" else "Уровень: ${Levels.title(level!!)}", { levelMenu = true }, filled = level != null) {
                        LineIcon(LineIcons.FILTER, if (level != null) Fc.Lime else Fc.Text, Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = levelMenu, onDismissRequest = { levelMenu = false }) {
                        listOf<Int?>(null, 1, 2, 3).forEach { l ->
                            DropdownMenuItem(text = { Text(if (l == null) "Все уровни" else "${Levels.title(l)} · ${inGroup.count { it.level == l }}") },
                                onClick = { level = l; levelMenu = false },
                                trailingIcon = { if (level == l) LineIcon(LineIcons.CHECK, Fc.Lime, Modifier.size(18.dp)) })
                        }
                    }
                }
            }
        }
        items(shown, key = { it.id }) { e -> ExerciseRow(e, onClick = { onExercise(e.id) }, onAdd = { onAdd(e.id) }) }
        if (shown.isEmpty()) item { Text("Нет упражнений с такими фильтрами", color = Fc.Muted) }
    }
}

/** Technique text as numbered steps: one sentence per step. */
internal fun techniqueSteps(text: String): List<String> =
    text.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }

@Composable
fun ExerciseDetailScreen(exerciseId: String, onBack: () -> Unit, onOpenExercise: (String) -> Unit, onAddToProgram: () -> Unit, trainingContext: Boolean = false) {
    val vm = appViewModel { LibraryViewModel(it) }
    val all by vm.exercises.collectAsStateWithLifecycle()
    val profile by vm.settings.collectAsStateWithLifecycle()
    val e = all.firstOrNull { it.id == exerciseId }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    if (e == null) {
        Column(Modifier.padding(20.dp)) { BackRow("Справочник", onBack); Text(if (all.isEmpty()) "Загрузка упражнения…" else "Упражнение не найдено", color = Fc.Muted) }
        return
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).testTag("exercise-detail-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { BackRow(e.group.title, onBack) }
            FreeExerciseCatalog.media[e.id]?.let { media ->
                item { ExerciseMediaCard(e.id, e.name, media) }
            }
            item {
                Eyebrow(e.group.title)
                ScreenTitle(e.name, Modifier.padding(top = 2.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(e.equipment, Fc.LimeSoft, Fc.Lime)
                    Pill(categoryOf(e)?.takeIf { it == "Растяжка" } ?: e.type.title, Fc.Surface2, Fc.Text)
                    Pill(Levels.title(e.level), Fc.Surface2, Fc.Text)
                }
            }
            item { Segmented(listOf(0, 1, 2), tab, { tab = it }, { listOf("Техника", "Мышцы", "Замены")[it] }, selectedBg = Fc.Lime) }
            when (tab) {
                0 -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            techniqueSteps(e.technique).forEachIndexed { i, step ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(Fc.Surface2), contentAlignment = Alignment.Center) {
                                        NumberText("${i + 1}", 14.sp)
                                    }
                                    Text(step, style = MaterialTheme.typography.bodyLarge, color = Fc.Text, modifier = Modifier.padding(top = 3.dp))
                                }
                            }
                        }
                    }
                    item { MuscleSummary(e, profile.sex) }
                }
                1 -> {
                    item { MuscleSummary(e, profile.sex) }
                    if (e.group == MuscleGroup.QUADS) item { QuadricepsLegend() }
                    item { ExerciseMuscleMap(profile.sex, e.primary, e.secondary) }
                    item {
                        FcCard {
                            Text(e.impact, color = Fc.Text)
                            Spacer(Modifier.height(12.dp))
                            Text("Уровень: ${Levels.title(e.level)}", color = Fc.Lime, style = MaterialTheme.typography.labelLarge)
                            Text(Levels.explanation(e.level), color = Fc.Muted)
                        }
                    }
                }
                else -> {
                    if (e.alternatives.isEmpty()) item { Text("Замены для этого упражнения пока не указаны.", color = Fc.Muted) }
                    items(e.alternatives) { alt ->
                        val linked = all.firstOrNull { it.name == alt.name }
                        FcCard(Modifier.fillMaxWidth(), onClick = linked?.let { { onOpenExercise(it.id) } }) {
                            Text(alt.name, style = MaterialTheme.typography.titleSmall, color = Fc.Text)
                            Text(alt.why, style = MaterialTheme.typography.bodySmall, color = Fc.Muted)
                        }
                    }
                }
            }
            if (!trainingContext) item { PrimaryButton("+ Добавить в программу", onAddToProgram) }
            else item { PrimaryButton("Вернуться к тренировке", onBack) }
        }
    }
}

@Composable
private fun MuscleSummary(e: ExerciseEntity, profileSex: Sex) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Fc.Surface).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupImage(profileSex, e.group, Modifier.size(64.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Основные мышцы: " + e.primary.joinToString(", ") { it.title }, color = Fc.Text, style = MaterialTheme.typography.bodyMedium)
            if (e.secondary.isNotEmpty()) Text("Дополнительно: " + e.secondary.joinToString(", ") { it.title }, color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

