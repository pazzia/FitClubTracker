package com.fitclub.tracker.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.model.BodyView
import com.fitclub.tracker.data.model.ExerciseType
import com.fitclub.tracker.data.model.Levels
import com.fitclub.tracker.data.model.MuscleGroup
import com.fitclub.tracker.data.model.Sex
import com.fitclub.tracker.data.repo.ExerciseRepository
import com.fitclub.tracker.ui.components.BackRow
import com.fitclub.tracker.ui.components.FcCard
import com.fitclub.tracker.ui.components.MuscleFigure
import com.fitclub.tracker.ui.components.Pill
import com.fitclub.tracker.ui.components.PrimaryButton
import com.fitclub.tracker.ui.components.ScreenTitle
import com.fitclub.tracker.ui.components.Segmented
import com.fitclub.tracker.ui.navigation.appViewModel
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LibraryViewModel(repo: ExerciseRepository) : ViewModel() {
    val exercises: StateFlow<List<ExerciseEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Пол модели. На этапе 3 будет браться из личного кабинета. */
    val sex = MutableStateFlow(Sex.MALE)
}

private val HeroBrush = Brush.linearGradient(listOf(Color(0xFF2B2F38), Color(0xFF15171C)))

private fun plural(n: Int, one: String, few: String, many: String): String {
    val t = n % 100; val u = n % 10
    return when { t in 11..19 -> many; u == 1 -> one; u in 2..4 -> few; else -> many }
}

private fun exercisesLabel(n: Int) = "$n ${plural(n, "упражнение", "упражнения", "упражнений")}"

// ---------- Группы мышц ----------

@Composable
fun LibraryHomeScreen(onGroup: (MuscleGroup) -> Unit, onExercise: (String) -> Unit) {
    val vm = appViewModel { LibraryViewModel(it.exercises) }
    val all by vm.exercises.collectAsStateWithLifecycle()
    val sex by vm.sex.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val results = if (q.isEmpty()) emptyList() else all.filter { it.name.lowercase().contains(q) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ScreenTitle("Справочник")
                Text("${exercisesLabel(all.size)} · ${MuscleGroup.entries.size} групп мышц", style = MaterialTheme.typography.bodyMedium, color = Fc.Muted)
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Найти упражнение") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Fc.Surface, unfocusedContainerColor = Fc.Surface,
                    focusedBorderColor = Fc.Lime, unfocusedBorderColor = Fc.Line,
                    focusedTextColor = Fc.Text, unfocusedTextColor = Fc.Text, cursorColor = Fc.Lime
                )
            )
        }
        if (q.isNotEmpty()) {
            item { Text(if (results.isEmpty()) "Ничего не найдено" else "Найдено: ${results.size}", style = MaterialTheme.typography.labelMedium, color = Fc.Muted) }
            items(results, key = { it.id }) { e -> ExerciseRow(e, showGroup = true) { onExercise(e.id) } }
        } else {
            val counts = all.groupingBy { it.group }.eachCount()
            items(MuscleGroup.entries.chunked(2)) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { g ->
                        GroupTile(g, counts[g] ?: 0, sex, Modifier.weight(1f)) { onGroup(g) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GroupTile(group: MuscleGroup, count: Int, sex: Sex, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(132.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Fc.Surface)
            .border(1.dp, Color(0xFF26262F), RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 6.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MuscleFigure(sex, group.view, target = group.regions, modifier = Modifier.width(56.dp), detailed = false)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(group.title.uppercase(), style = MaterialTheme.typography.headlineSmall, color = Fc.Text)
            Text(exercisesLabel(count), style = MaterialTheme.typography.labelMedium, color = Fc.Muted)
        }
    }
}

@Composable
private fun ExerciseRow(e: ExerciseEntity, showGroup: Boolean = false, onClick: () -> Unit) {
    FcCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(e.name, style = MaterialTheme.typography.titleSmall, color = Fc.Text)
                Text(
                    (if (showGroup) e.group.title + " · " else "") + e.type.title + " · " + e.equipment,
                    style = MaterialTheme.typography.bodySmall, color = Fc.Muted
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Fc.Lime, modifier = Modifier.size(14.dp))
                    Text(" " + "%.1f".format(e.communityRating).replace('.', ','), style = MaterialTheme.typography.labelMedium, color = Color(0xFFC4C4CE))
                }
            }
            val c = Fc.level(e.level)
            Pill(Levels.title(e.level), c.copy(alpha = 0.16f), c)
        }
    }
}

// ---------- Упражнения группы ----------

@Composable
fun LibraryGroupScreen(group: MuscleGroup, onBack: () -> Unit, onExercise: (String) -> Unit) {
    val vm = appViewModel { LibraryViewModel(it.exercises) }
    val all by vm.exercises.collectAsStateWithLifecycle()
    val sex by vm.sex.collectAsStateWithLifecycle()
    var type by rememberSaveable { mutableStateOf<ExerciseType?>(null) }
    var level by rememberSaveable { mutableStateOf<Int?>(null) }
    val inGroup = all.filter { it.group == group }
    val shown = inGroup.filter { (type == null || it.type == type) && (level == null || it.level == level) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackRow("Группы мышц", onBack) }
        item {
            Row(
                Modifier.fillMaxWidth().height(212.dp).clip(RoundedCornerShape(22.dp)).background(HeroBrush)
                    .padding(start = 16.dp, end = 6.dp)
            ) {
                Column(Modifier.weight(1f).padding(vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ScreenTitle(group.title)
                        Text(exercisesLabel(inGroup.size), style = MaterialTheme.typography.bodyMedium, color = Fc.Muted)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).shadow(6.dp, CircleShape, ambientColor = Fc.GlowBlue, spotColor = Fc.GlowBlue).clip(CircleShape).background(Fc.TargetRed))
                            Text("  Целевая зона", style = MaterialTheme.typography.labelMedium, color = Color(0xFFC4C4CE))
                        }
                        Text(group.regions.joinToString(", ") { it.title }, style = MaterialTheme.typography.titleSmall, color = Fc.Text)
                    }
                }
                MuscleFigure(
                    sex, group.view, target = group.regions, modifier = Modifier.width(96.dp).padding(top = 1.dp),
                    description = "Модель с подсвеченной группой: ${group.title}"
                )
            }
        }
        item {
            Column(
                Modifier.clip(RoundedCornerShape(18.dp)).background(Fc.Surface).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Segmented(
                    options = listOf(null, ExerciseType.BASIC, ExerciseType.ISOLATION),
                    selected = type, onSelect = { type = it },
                    label = { when (it) { null -> "Все"; ExerciseType.BASIC -> "Базовые"; ExerciseType.ISOLATION -> "Изоляция" } },
                    trailing = { opt, on ->
                        val cnt = inGroup.count { (opt == null || it.type == opt) && (level == null || it.level == level) }
                        Text("  $cnt", style = MaterialTheme.typography.labelSmall, color = if (on) Color(0xFF4A4F5C) else Color(0xFF6A6A76))
                    }
                )
                Segmented(
                    options = listOf(null, 1, 2, 3),
                    selected = level, onSelect = { level = it },
                    label = { when (it) { null -> "Все"; 1 -> "Новичок"; 2 -> "Продв."; else -> "Профи" } },
                    leading = { opt, on ->
                        if (opt != null) {
                            LevelBars(opt, on)
                            Spacer(Modifier.width(5.dp))
                        }
                    }
                )
            }
        }
        items(shown, key = { it.id }) { e -> ExerciseRow(e) { onExercise(e.id) } }
        if (shown.isEmpty()) item {
            Text("Нет упражнений с такими фильтрами.", style = MaterialTheme.typography.bodyMedium, color = Fc.Muted, modifier = Modifier.padding(vertical = 20.dp))
        }
    }
}

@Composable
private fun LevelBars(level: Int, selected: Boolean) {
    val col = Fc.level(level)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
        listOf(5, 8, 11).forEachIndexed { i, h ->
            val filled = i < level
            Box(
                Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(1.dp)).background(
                    when {
                        filled && selected -> Fc.Bg
                        filled -> col
                        selected -> Color(0xFFB9BBC2)
                        else -> Color(0xFF3A3A46)
                    }
                )
            )
        }
    }
}

// ---------- Карточка упражнения ----------

@Composable
fun ExerciseDetailScreen(
    exerciseId: String,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onAddToProgram: () -> Unit
) {
    val vm = appViewModel { LibraryViewModel(it.exercises) }
    val all by vm.exercises.collectAsStateWithLifecycle()
    val sexState by vm.sex.collectAsStateWithLifecycle()
    var sex by rememberSaveable { mutableStateOf(sexState) }
    val e = all.firstOrNull { it.id == exerciseId } ?: return

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BackRow(e.group.title, onBack) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(e.name.uppercase(), style = MaterialTheme.typography.headlineMedium, color = Fc.Text)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(e.type.title, Fc.Text, Fc.Bg)
                    val c = Fc.level(e.level)
                    Pill(Levels.title(e.level), c.copy(alpha = 0.16f), c)
                    Pill(e.equipment, Fc.Surface2, Color(0xFFC4C4CE))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Fc.Lime, modifier = Modifier.size(18.dp))
                    Text("%.1f".format(e.communityRating).replace('.', ','), style = MaterialTheme.typography.titleMedium, color = Fc.Text)
                    Text("${e.communityVotes} оценок сообщества", style = MaterialTheme.typography.bodySmall, color = Fc.Muted)
                }
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(20.dp)).background(Fc.Surface),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Fc.Lime), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Анимация техники", tint = Fc.Bg)
                    }
                    Text("Анимация техники появится в следующей версии", style = MaterialTheme.typography.labelMedium, color = Fc.Muted)
                }
            }
        }
        item {
            FcCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Нагружаемые мышцы", style = MaterialTheme.typography.titleSmall, color = Fc.Text, modifier = Modifier.weight(1f))
                    Segmented(
                        options = Sex.entries, selected = sex, onSelect = { sex = it },
                        label = { if (it == Sex.MALE) "Мужская" else "Женская" },
                        modifier = Modifier.width(190.dp), height = 36.dp,
                        selectedBg = Fc.Surface2, selectedFg = Fc.Text
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(HeroBrush).padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(BodyView.FRONT to "Спереди", BodyView.BACK to "Сзади").forEach { (view, title) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MuscleFigure(sex, view, target = e.primary, aux = e.secondary, modifier = Modifier.width(140.dp), description = "Модель: $title")
                            Text(title, style = MaterialTheme.typography.labelMedium, color = Fc.Muted)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Красным подсвечены целевые мышцы — их упражнение нагружает в первую очередь. Оранжевым — вспомогательные, которые помогают выполнить движение.",
                    style = MaterialTheme.typography.bodySmall, color = Fc.Muted
                )
                Spacer(Modifier.height(8.dp))
                LegendLine(Fc.TargetRed, true, "Целевые: ", e.primary.joinToString(", ") { it.title })
                if (e.secondary.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    LegendLine(Fc.AuxOrange, false, "Вспомогательные: ", e.secondary.joinToString(", ") { it.title })
                }
            }
        }
        item { TextSection("Техника выполнения", e.technique) }
        item { TextSection("Воздействие", e.impact) }
        item {
            FcCard {
                Text("Сложность", style = MaterialTheme.typography.titleSmall, color = Fc.Text)
                Spacer(Modifier.height(10.dp))
                Text("Тип: ${e.type.title}", style = MaterialTheme.typography.labelLarge, color = Fc.Text)
                Text(e.type.explanation, style = MaterialTheme.typography.bodySmall, color = Fc.Muted)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Уровень: ${Levels.title(e.level)}", style = MaterialTheme.typography.labelLarge, color = Fc.Text, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) { i ->
                            Box(Modifier.width(22.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (i < e.level) Fc.level(e.level) else Fc.Line))
                        }
                    }
                }
                Text(Levels.explanation(e.level), style = MaterialTheme.typography.bodySmall, color = Fc.Muted)
            }
        }
        item { Text("Чем заменить", style = MaterialTheme.typography.titleSmall, color = Fc.Text) }
        items(e.alternatives) { alt ->
            val hit = all.firstOrNull { it.name == alt.name }
            FcCard(onClick = hit?.let { h -> { onOpenExercise(h.id) } }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(alt.name, style = MaterialTheme.typography.titleSmall, color = Fc.Text)
                        Text(alt.why, style = MaterialTheme.typography.bodySmall, color = Fc.Muted)
                    }
                    if (hit != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Открыть", tint = Fc.Lime)
                }
            }
        }
        item { PrimaryButton("Добавить в программу", onAddToProgram) }
    }
}

@Composable
private fun LegendLine(color: Color, glow: Boolean, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 3.dp).size(14.dp)
                .then(if (glow) Modifier.shadow(6.dp, CircleShape, ambientColor = Fc.GlowBlue, spotColor = Fc.GlowBlue) else Modifier)
                .clip(CircleShape).background(color)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.ExtraBold)); append(label); pop()
                append(value)
            },
            style = MaterialTheme.typography.bodyMedium, color = Fc.Text
        )
    }
}

@Composable
private fun TextSection(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Fc.Text)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC4C4CE))
    }
}
