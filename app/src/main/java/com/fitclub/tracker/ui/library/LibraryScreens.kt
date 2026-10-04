package com.fitclub.tracker.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitclub.tracker.AppContainer
import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.seed.FreeExerciseCatalog
import com.fitclub.tracker.data.model.*
import com.fitclub.tracker.ui.components.*
import com.fitclub.tracker.ui.navigation.appViewModel
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.flow.*

class LibraryViewModel(c: AppContainer) : ViewModel() {
    val exercises = c.exercises.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = c.profile.settings
}


@Composable
fun LibraryHomeScreen(onGroup: (MuscleGroup) -> Unit, onExercise: (String) -> Unit) {
    val vm = appViewModel { LibraryViewModel(it) }
    val all by vm.exercises.collectAsStateWithLifecycle()
    val profile by vm.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val results = all.filter { it.name.lowercase().contains(q) || it.group.title.lowercase().contains(q) ||
        FreeExerciseCatalog.media[it.id]?.sourceId?.replace('_', ' ')?.lowercase()?.contains(q) == true }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("ИЗУЧАЙ. ВЫБИРАЙ. ТРЕНИРУЙСЯ.", style = MaterialTheme.typography.labelSmall, color = Fc.Muted)
            ScreenTitle("Справочник")
        }
        item {
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск упражнения или группы…", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true, shape = RoundedCornerShape(14.dp))
        }
        if (q.isNotEmpty()) {
            item { Text(if (results.isEmpty()) "Ничего не найдено" else "Найдено: ${results.size}", color = Fc.Muted) }
            items(results, key = { it.id }) { e -> ExerciseRow(e, profile.sex, onClick = { onExercise(e.id) }) }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Группы мышц", style = MaterialTheme.typography.titleMedium, color = Fc.Text)
                    Text("${AnatomyGroupOrder.size} групп", style = MaterialTheme.typography.bodySmall, color = Fc.Muted)
                }
            }
            val counts = all.groupingBy { it.group }.eachCount()
            items(AnatomyGroupOrder.chunked(2)) { pair ->
                MuscleCategoryRow(pair, profile.sex, counts, onGroup)
            }
            item { AnatomyCard(profile.sex) }
        }
    }
}

@Composable
private fun ExerciseRow(e: ExerciseEntity, sex: Sex, onClick: () -> Unit) {
    FcCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExerciseThumbnail(e, Modifier.size(64.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(e.name, style = MaterialTheme.typography.titleSmall, color = Fc.Text)
                val category = FreeExerciseCatalog.media[e.id]?.category
                Text("${if (category == "Растяжка") category else e.type.title} · ${e.equipment}", style = MaterialTheme.typography.bodySmall, color = Fc.Muted)
            }
            Text("›", color = Fc.Muted)
        }
    }
}

@Composable
fun LibraryGroupScreen(group: MuscleGroup, onBack: () -> Unit, onExercise: (String) -> Unit) {
    val vm = appViewModel { LibraryViewModel(it) }
    val all by vm.exercises.collectAsStateWithLifecycle()
    val profile by vm.settings.collectAsStateWithLifecycle()
    var type by rememberSaveable { mutableStateOf<ExerciseType?>(null) }
    var level by rememberSaveable { mutableStateOf<Int?>(null) }
    val inGroup = all.filter { it.group == group }
    val shown = inGroup.filter { (type == null || (it.type == type && FreeExerciseCatalog.media[it.id]?.category != "Растяжка")) && (level == null || it.level == level) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BackRow("Справочник", onBack); ScreenTitle(group.title) }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Fc.Surface)) {
                GroupImage(profile.sex, group, Modifier.fillMaxWidth().height(205.dp))
                Row(Modifier.fillMaxWidth().padding(17.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${inGroup.size} упражнений", style = MaterialTheme.typography.titleSmall, color = Fc.Text)
                    Pill(if (group == MuscleGroup.QUADS) "4 головки" else "${group.regions.size} зон", Fc.LimeSoft, Fc.Lime)
                }
            }
        }
        if (group == MuscleGroup.QUADS) item { QuadricepsLegend() }
        item {
            Segmented(listOf(null, ExerciseType.BASIC, ExerciseType.ISOLATION), type, { type = it },
                { when (it) { null -> "Все"; ExerciseType.BASIC -> "Базовые"; else -> "Изоляция" } }, selectedBg = Fc.Lime)
        }
        item {
            Segmented(listOf(null, 1, 2, 3), level, { level = it },
                { when (it) { null -> "Все уровни"; 1 -> "Новичок"; 2 -> "Продв."; else -> "Профи" } }, selectedBg = Fc.Lime)
        }
        items(shown, key = { it.id }) { e -> ExerciseRow(e, profile.sex) { onExercise(e.id) } }
        if (shown.isEmpty()) item { Text("Нет упражнений с такими фильтрами", color = Fc.Muted) }
    }
}

@Composable
private fun AnatomyCard(sex: Sex) {
    FcCard {
        Text("Карта мышечных групп", style = MaterialTheme.typography.titleSmall, color = Fc.Text)
        Spacer(Modifier.height(12.dp))
        BodyPair(sex, Modifier.clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(12.dp))
        Text("Цвета обозначают группы мышц, а не интенсивность нагрузки. Модель соответствует полу в личном кабинете.",
            style = MaterialTheme.typography.bodySmall, color = Fc.Muted)
    }
}

@Composable
fun ExerciseDetailScreen(exerciseId: String, onBack: () -> Unit, onOpenExercise: (String) -> Unit, onAddToProgram: () -> Unit) {
    val vm = appViewModel { LibraryViewModel(it) }
    val all by vm.exercises.collectAsStateWithLifecycle()
    val profile by vm.settings.collectAsStateWithLifecycle()
    val e = all.firstOrNull { it.id == exerciseId }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    if (e == null) {
        Column(Modifier.padding(20.dp)) { BackRow("Справочник", onBack); Text("Загрузка упражнения…", color = Fc.Muted) }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BackRow(e.group.title, onBack); ScreenTitle(e.name) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(e.equipment, Fc.LimeSoft, Fc.Lime)
                Pill(FreeExerciseCatalog.media[e.id]?.category?.takeIf { it == "Растяжка" } ?: e.type.title, Fc.Surface2, Fc.Text)
            }
        }
        item { GroupImage(profile.sex, e.group, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp))) }
        FreeExerciseCatalog.media[e.id]?.let { media ->
            item { ExerciseMediaCard(e.id, e.name, media) }
        }
        item {
            FcCard {
                Text("Основные мышцы", color = Fc.Lime, style = MaterialTheme.typography.titleSmall)
                Text(e.primary.joinToString(", ") { it.title }, color = Fc.Text)
                if (e.secondary.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp)); Text("Дополнительно: " + e.secondary.joinToString(", ") { it.title }, color = Fc.Muted)
                }
            }
        }
        if (e.group == MuscleGroup.QUADS) item { QuadricepsLegend() }
        item { ExerciseMuscleMap(profile.sex, e.primary, e.secondary) }
        item { Segmented(listOf(0, 1, 2), tab, { tab = it }, { listOf("Техника", "Описание", "Замены")[it] }, selectedBg = Fc.Lime) }
        when (tab) {
            0 -> item { FcCard { Text(e.technique, style = MaterialTheme.typography.bodyMedium, color = Fc.Text) } }
            1 -> item { FcCard { Text(e.impact, color = Fc.Text); Spacer(Modifier.height(12.dp)); Text("Уровень: ${Levels.title(e.level)}", color = Fc.Lime); Text(Levels.explanation(e.level), color = Fc.Muted) } }
            2 -> items(e.alternatives) { alt ->
                val linked = all.firstOrNull { it.name == alt.name }
                FcCard(onClick = linked?.let { { onOpenExercise(it.id) } }) {
                    Text(alt.name, style = MaterialTheme.typography.titleSmall, color = Fc.Text)
                    Text(alt.why, style = MaterialTheme.typography.bodySmall, color = Fc.Muted)
                }
            }
        }
        item { PrimaryButton("+ Добавить в программу", onAddToProgram) }
    }
}
