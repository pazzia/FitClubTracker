package com.fitclub.tracker.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitclub.tracker.AppContainer
import com.fitclub.tracker.data.db.BodyMeasurementEntity
import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.model.Sex
import com.fitclub.tracker.data.repo.*
import com.fitclub.tracker.ui.components.*
import com.fitclub.tracker.ui.navigation.appViewModel
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

data class DashboardState(
    val program: ProgramDraft? = null,
    val exercises: Map<String, ExerciseEntity> = emptyMap(),
    val workouts: List<WorkoutLog> = emptyList(),
    val measurements: List<BodyMeasurementEntity> = emptyList()
)

class DashboardViewModel(c: AppContainer) : ViewModel() {
    val state = combine(
        c.programs.observeActive(), c.exercises.observeAll(), c.fitness.observeWorkouts(), c.fitness.observeMeasurements()
    ) { program, exercises, workouts, measurements ->
        DashboardState(program, exercises.associateBy { it.id }, workouts, measurements)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())
}

@Composable
fun TodayScreen(onStart: (Int) -> Unit, onPrograms: () -> Unit, onProfile: () -> Unit) {
    val vm = appViewModel { DashboardViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val p = s.program
    val day = if (p == null || p.days.isEmpty()) 0 else s.workouts.size % p.days.size
    val items = p?.days?.getOrNull(day)?.items.orEmpty()
    val last = s.workouts.firstOrNull()
    val weight = s.measurements.lastOrNull()?.weightKg
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru")))

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    ScreenTitle("Сегодня")
                    Text(date.replaceFirstChar { it.uppercase() }, color = Fc.Muted, style = MaterialTheme.typography.bodyMedium)
                }
                Surface(onClick = onProfile, color = Fc.Surface2, shape = CircleShape, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Person, "Личный кабинет", tint = Fc.Lime, modifier = Modifier.padding(10.dp))
                }
            }
        }
        item {
            FcCard(border = Fc.Lime.copy(alpha = .55f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Pill("СЕГОДНЯ · ДЕНЬ ${'A' + day}", Fc.LimeSoft, Fc.Lime)
                        Text(p?.name ?: "Выберите программу", style = MaterialTheme.typography.headlineSmall, color = Fc.Text)
                        Text("~55 мин · ${items.size} упр. · ${items.sumOf { it.sets }} подходов", color = Fc.Muted)
                    }
                    ProgramCover(p?.icon ?: ProgramIcons.TARGET, Color(p?.colorArgb ?: 0xFFD4FF3A), 58.dp)
                }
                Spacer(Modifier.height(12.dp))
                items.take(4).forEach { item ->
                    val e = s.exercises[item.exerciseId]
                    Row(Modifier.padding(vertical = 5.dp)) {
                        Text(e?.name ?: item.exerciseId, color = Fc.Text, modifier = Modifier.weight(1f))
                        Text("${item.sets} × ${item.reps.label}", color = Fc.Muted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                PrimaryButton(if (p == null) "Выбрать программу" else "Начать тренировку", { if (p == null) onPrograms() else onStart(day) })
            }
        }
        item {
            FcCard(onClick = onPrograms) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ПРОГРАММА", style = MaterialTheme.typography.labelMedium, color = Fc.Muted)
                        Text(p?.name ?: "Нет активной", style = MaterialTheme.typography.titleMedium, color = Fc.Text)
                        Text("Неделя ${((s.workouts.size / (p?.freqPerWeek ?: 3)) + 1).coerceAtMost(8)} из 8", color = Fc.Muted)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Fc.Muted)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat("Вес тела", weight?.let { "${fmt(it)} кг" } ?: "—", "добавьте замер", Modifier.weight(1f))
                MiniStat("Прошлая", last?.let { "${it.volumeKg.roundToInt()} кг" } ?: "—", last?.let { "${it.durationMin} мин" } ?: "нет данных", Modifier.weight(1f))
            }
        }
        if (last != null) item {
            FcCard {
                Text("ПОСЛЕДНЯЯ ТРЕНИРОВКА", style = MaterialTheme.typography.labelMedium, color = Fc.Muted)
                Text("${last.completedSets} подходов · ${last.volumeKg.roundToInt()} кг объёма", style = MaterialTheme.typography.titleMedium, color = Fc.Text)
                Text("Данные сохранены и уже учтены в прогрессе", color = Fc.Lime, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable private fun MiniStat(title: String, value: String, note: String, modifier: Modifier) {
    FcCard(modifier) {
        Text(title, color = Fc.Muted, style = MaterialTheme.typography.labelMedium)
        Text(value, color = Fc.Text, style = MaterialTheme.typography.headlineSmall)
        Text(note, color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun CalendarScreen(onStart: (Int) -> Unit) {
    val vm = appViewModel { DashboardViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val month = remember { YearMonth.now() }
    var selected by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfMonth) }
    val firstOffset = month.atDay(1).dayOfWeek.value - 1
    val completed = s.workouts.map { LocalDate.ofEpochDay(it.session.epochDay).dayOfMonth }.toSet()
    val p = s.program
    val planned = (1..month.lengthOfMonth()).filter { month.atDay(it).dayOfWeek.value in setOf(2, 4, 6) }.toSet()
    val selectedDate = month.atDay(selected)
    val isDone = selected in completed
    val isPlanned = selected in planned

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenTitle("Календарь") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru")).uppercase(), style = MaterialTheme.typography.headlineSmall, color = Fc.Text, modifier = Modifier.weight(1f))
                Text("${completed.size} выполнено", color = Fc.Lime, style = MaterialTheme.typography.labelMedium)
            }
        }
        item {
            FcCard {
                Row(Modifier.fillMaxWidth()) { listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс").forEach { Text(it, color = Fc.Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
                Spacer(Modifier.height(6.dp))
                val cells = List(firstOffset) { 0 } + (1..month.lengthOfMonth()).toList()
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        (week + List(7 - week.size) { 0 }).forEach { d ->
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).then(if (d > 0) Modifier.clickable(role = Role.Button) { selected = d } else Modifier), contentAlignment = Alignment.Center) {
                                if (d > 0) {
                                    val color = when { d == selected -> Fc.Text; d in completed -> Fc.Lime; d in planned -> Fc.Surface2; else -> Color.Transparent }
                                    Box(Modifier.matchParentSize().background(color, CircleShape))
                                    Text("$d", color = if (d == selected || d in completed) Fc.Bg else Fc.Text, fontWeight = if (d == selected) FontWeight.ExtraBold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            FcCard(border = if (isDone) Fc.Lime.copy(.5f) else Fc.Line) {
                Text(selectedDate.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("ru"))).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, color = Fc.Text)
                Text(when { isDone -> "Выполнена"; isPlanned -> "Запланирована"; else -> "День отдыха" }, color = when { isDone -> Fc.Lime; isPlanned -> Fc.Orange; else -> Fc.Muted })
                if (isPlanned || isDone) Text("${p?.name ?: "Тренировка"} · день ${'A' + (planned.sorted().indexOf(selected).coerceAtLeast(0) % (p?.days?.size ?: 1))}", color = Fc.Muted)
                if (isPlanned && !isDone) {
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Начать тренировку", { onStart(planned.sorted().indexOf(selected).coerceAtLeast(0) % (p?.days?.size ?: 1)) })
                }
            }
        }
    }
}

@Composable
fun ProgressScreen(onProfile: () -> Unit) {
    val vm = appViewModel { DashboardViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val total = s.workouts.sumOf { it.volumeKg.toDouble() }.toFloat()
    val completedSets = s.workouts.sumOf { it.completedSets }
    val latest = s.measurements.lastOrNull()
    val first = s.measurements.firstOrNull()
    val weekly = s.workouts.groupBy { LocalDate.ofEpochDay(it.session.epochDay).dayOfYear / 7 }.values.take(6).map { w -> w.sumOf { it.volumeKg.toDouble() }.toFloat() }.ifEmpty { listOf(0f) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Прогресс", Modifier.weight(1f))
                TextButton(onClick = onProfile) { Text("Кабинет", color = Fc.Lime) }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("Тренировки", "${s.workouts.size}", "${completedSets} подходов", Modifier.weight(1f))
            MiniStat("Общий объём", if (total >= 1000) "${fmt(total / 1000)} т" else "${total.roundToInt()} кг", "за всё время", Modifier.weight(1f))
        } }
        item {
            FcCard {
                Text("ОБЪЁМ ПО НЕДЕЛЯМ", style = MaterialTheme.typography.labelMedium, color = Fc.Muted)
                Spacer(Modifier.height(12.dp))
                val max = weekly.maxOrNull()?.coerceAtLeast(1f) ?: 1f
                Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    weekly.forEachIndexed { i, v ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Text(if (v > 0) "${(v / 1000).let(::fmt)}" else "0", color = Fc.Muted, style = MaterialTheme.typography.labelSmall)
                            Box(Modifier.fillMaxWidth().height((112 * v / max).coerceAtLeast(4f).dp).background(if (i == weekly.lastIndex) Fc.Lime else Fc.Surface2, RoundedCornerShape(6.dp)))
                        }
                    }
                }
            }
        }
        item {
            FcCard(onClick = onProfile) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ВЕС ТЕЛА", style = MaterialTheme.typography.labelMedium, color = Fc.Muted)
                        Text(latest?.weightKg?.let { "${fmt(it)} кг" } ?: "Нет замеров", style = MaterialTheme.typography.headlineSmall, color = Fc.Text)
                        if (latest?.weightKg != null && first?.weightKg != null) Text("${signed(latest.weightKg - first.weightKg)} кг с первого замера", color = Fc.Lime)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Fc.Muted)
                }
            }
        }
        item {
            Text("ПО УПРАЖНЕНИЯМ", style = MaterialTheme.typography.labelMedium, color = Fc.Muted)
            val byExercise = s.workouts.flatMap { it.sets }.filter { it.done }.groupBy { it.exerciseId }.entries.sortedByDescending { it.value.maxOfOrNull { x -> x.weightKg } ?: 0f }.take(6)
            if (byExercise.isEmpty()) Text("Завершите первую тренировку — здесь появятся рабочие веса.", color = Fc.Muted, modifier = Modifier.padding(vertical = 14.dp))
            byExercise.forEach { (id, sets) ->
                val best = sets.maxByOrNull { it.weightKg * it.reps }!!
                FcCard(Modifier.padding(top = 8.dp)) {
                    Text(s.exercises[id]?.name ?: id, color = Fc.Text, style = MaterialTheme.typography.titleSmall)
                    Text("Лучший подход: ${fmt(best.weightKg)} кг × ${best.reps}", color = Fc.Muted)
                }
            }
        }
    }
}

class ProfileViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.profile.settings
    val measurements = c.fitness.observeMeasurements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workouts = c.fitness.observeWorkouts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun update(value: ProfileSettings) = c.profile.update(value)
    fun add(weight: Float?, waist: Float?, fat: Float?, chest: Float?, arm: Float?) = viewModelScope.launch { c.fitness.addMeasurement(weight, waist, fat, chest, arm) }
}

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val vm = appViewModel { ProfileViewModel(it) }
    val st by vm.settings.collectAsStateWithLifecycle()
    val measurements by vm.measurements.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var weight by rememberSaveable { mutableStateOf("") }; var waist by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }; var chest by rememberSaveable { mutableStateOf("") }; var arm by rememberSaveable { mutableStateOf("") }
    val latest = measurements.lastOrNull()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BackRow("Прогресс", onBack) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(68.dp).background(Fc.Lime, CircleShape), contentAlignment = Alignment.Center) { Text("АК", style = MaterialTheme.typography.headlineSmall, color = Fc.Bg) }
                Column { ScreenTitle(st.name); Text("${if (st.level == 1) "Новичок" else if (st.level == 2) "Продвинутый" else "Профи"} · в клубе с сентября 2026", color = Fc.Muted) }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("Тренировок", "${workouts.size}", "сохранено", Modifier.weight(1f)); MiniStat("Замеров", "${measurements.size}", "параметров", Modifier.weight(1f))
        } }
        item {
            FcCard {
                Text("ПАРАМЕТРЫ ТЕЛА", style = MaterialTheme.typography.labelMedium, color = Fc.Muted)
                Spacer(Modifier.height(8.dp))
                listOf("Вес" to latest?.weightKg?.let { "${fmt(it)} кг" }, "Талия" to latest?.waistCm?.let { "${fmt(it)} см" }, "Жир" to latest?.fatPercent?.let { "${fmt(it)}%" }, "Грудь" to latest?.chestCm?.let { "${fmt(it)} см" }, "Рука" to latest?.armCm?.let { "${fmt(it)} см" }).forEach { (k,v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Text(k, color = Fc.Muted, modifier = Modifier.weight(1f)); Text(v ?: "—", color = Fc.Text) }
                }
                Spacer(Modifier.height(8.dp)); PrimaryButton("Добавить замер", { adding = true })
            }
        }
        if (adding) item {
            FcCard(border = Fc.Lime.copy(.5f)) {
                Text("НОВЫЙ ЗАМЕР · СЕГОДНЯ", style = MaterialTheme.typography.titleMedium, color = Fc.Text)
                MeasureField("Вес, кг", weight) { weight = it }; MeasureField("Талия, см", waist) { waist = it }; MeasureField("Жир, %", fat) { fat = it }; MeasureField("Грудь, см", chest) { chest = it }; MeasureField("Рука, см", arm) { arm = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { adding = false }, modifier = Modifier.weight(1f)) { Text("Отмена", color = Fc.Muted) }
                    Button(onClick = { vm.add(num(weight), num(waist), num(fat), num(chest), num(arm)); adding = false }, colors = ButtonDefaults.buttonColors(containerColor = Fc.Lime), modifier = Modifier.weight(1f)) { Text("Сохранить", color = Fc.Bg) }
                }
            }
        }
        item {
            FcCard {
                Text("О СЕБЕ", style = MaterialTheme.typography.labelMedium, color = Fc.Muted)
                Text("Пол", color = Fc.Text, modifier = Modifier.padding(top = 10.dp)); Segmented(Sex.entries, st.sex, { vm.update(st.copy(sex = it)) }, { it.title })
                Text("Уровень подготовки", color = Fc.Text, modifier = Modifier.padding(top = 12.dp)); Segmented(listOf(1,2,3), st.level, { vm.update(st.copy(level = it)) }, { if (it==1) "Новичок" else if (it==2) "Продв." else "Профи" })
                Text("Пол меняет модель тела в справочнике и отчётах.", color = Fc.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            FcCard {
                Text("ОФОРМЛЕНИЕ", style = MaterialTheme.typography.labelMedium, color = Fc.Muted)
                listOf("Volt Night", "Hot Pulse", "Cobalt Chalk").forEach { name ->
                    Row(Modifier.fillMaxWidth().clickable { vm.update(st.copy(theme = name)) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).background(if (name == "Volt Night") Fc.Lime else if (name == "Hot Pulse") Fc.Pink else Color(0xFF2B3BFF), CircleShape)); Text(name, color = Fc.Text, modifier = Modifier.padding(start = 10.dp).weight(1f)); if (st.theme == name) Icon(Icons.Filled.Check, null, tint = Fc.Lime)
                    }
                }
            }
        }
        item {
            FcCard {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Напоминания о тренировке", color = Fc.Text); Text("за 2 часа до начала", color = Fc.Muted) }; Switch(st.reminders, { vm.update(st.copy(reminders = it)) }, colors = SwitchDefaults.colors(checkedThumbColor = Fc.Bg, checkedTrackColor = Fc.Lime)) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Единицы веса", color = Fc.Text, modifier = Modifier.weight(1f)); TextButton(onClick = { vm.update(st.copy(unitsKg = !st.unitsKg)) }) { Text(if (st.unitsKg) "Килограммы" else "Фунты", color = Fc.Lime) } }
            }
        }
    }
}

@Composable private fun MeasureField(label: String, value: String, onChange: (String) -> Unit) { OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Fc.Text, unfocusedTextColor = Fc.Text, focusedBorderColor = Fc.Lime, unfocusedBorderColor = Fc.Line)) }
private fun num(s: String) = s.replace(',', '.').toFloatOrNull()
private fun fmt(v: Float) = if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else "%.1f".format(Locale.US, v).replace('.', ',')
private fun signed(v: Float) = (if (v > 0) "+" else "") + fmt(v)
