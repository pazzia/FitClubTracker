package com.fitclub.tracker.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitclub.tracker.AppContainer
import com.fitclub.tracker.data.db.SetLogEntity
import com.fitclub.tracker.data.repo.*
import com.fitclub.tracker.ui.components.*
import com.fitclub.tracker.ui.dashboard.calendarDate
import com.fitclub.tracker.ui.navigation.appViewModel
import com.fitclub.tracker.ui.programs.ExercisePicker
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WorkoutResultViewModel(private val c: AppContainer) : ViewModel() {
    val workouts = c.fitness.observeWorkouts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val catalog = c.exercises.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val plans = c.schedule.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    fun save(log: WorkoutLog, rows: List<Pair<SetLogEntity, SetEntry>>, minutes: Int, onSaved: () -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            runCatching {
                c.fitness.updateWorkout(log.session.id, log.session.startedAt,
                    log.session.startedAt + minutes * 60_000L,
                    rows.map { (old, row) -> old.copy(weightKg = row.weight.replace(',', '.').toFloat(), reps = row.reps.toInt(), done = row.done) })
            }.onSuccess { error = null; onSaved() }.onFailure { error = "Не удалось сохранить: ${it.message}" }
            saving = false
        }
    }
}

@Composable
fun WorkoutResultScreen(sessionId: Long, onClose: () -> Unit) {
    val vm = appViewModel(key = "result-$sessionId") { WorkoutResultViewModel(it) }
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()
    val log = workouts.firstOrNull { it.session.id == sessionId }
    if (log == null) {
        Column(Modifier.padding(20.dp)) { BackRow("Календарь", onClose); Text("Загружаю результат…", color = Fc.Muted) }
        return
    }
    var editing by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var minutes by remember { mutableStateOf(log.durationMin.toString()) }
    val rows = remember { mutableStateListOf<Pair<SetLogEntity, SetEntry>>() }
    fun beginEditing() {
        rows.clear(); rows.addAll(log.sets.map { it to SetEntry(it.weightKg.toString(), it.reps.toString(), it.done) })
        minutes = log.durationMin.toString(); editing = true
    }
    val close = { if (editing) discard = true else onClose() }
    BackHandler { if (picker) picker = false else close() }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Отменить изменения?") },
        confirmButton = { TextButton(onClick = { editing = false; discard = false }) { Text("Отменить изменения") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Продолжить редактирование") } })
    if (picker) {
        ExercisePicker(catalog, emptySet(), "Добавить подход", onBack = { picker = false }, onPick = { id ->
            val timed = log.sets.firstOrNull { it.exerciseId == id }?.isTimed == true
            rows.add(SetLogEntity(sessionId = sessionId, exerciseId = id, setIndex = rows.size,
                weightKg = 0f, reps = if (timed) 45 else 8, done = true, isTimed = timed) to SetEntry("0", if (timed) "45" else "8", true))
            picker = false
        })
        return
    }
    val valid = minutes.toIntOrNull()?.let { it in 1..1440 } == true && rows.all { (_, row) -> validSet(row) }
    val ids = if (editing) rows.map { it.first.exerciseId }.distinct() else
        (log.exercises.sortedBy { it.position }.map { it.exerciseId } + log.sets.map { it.exerciseId }).distinct()
    val volume = if (editing) rows.filter { !it.first.isTimed && it.second.done && validSet(it.second) }
        .sumOf { it.second.weight.replace(',', '.').toDouble() * it.second.reps.toInt() } else log.volumeKg.toDouble()
    val count = if (editing) rows.count { it.second.done } else log.completedSets
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BackRow("Календарь", close) }
        item {
            Text(calendarDate(log.session.epochDay), color = Fc.Muted)
            ScreenTitle(if (editing) "Редактирование" else "Результат тренировки")
            Text(plans.firstOrNull { it.id == log.session.scheduleId }?.title ?: "Тренировка", color = Fc.Text)
        }
        item {
            FcCard {
                Text("Общий тоннаж: ${volume.toInt()} кг", color = Fc.Lime, style = MaterialTheme.typography.titleMedium)
                Text("$count подходов · ${if (editing) minutes else log.durationMin.toString()} мин", color = Fc.Muted)
                if (editing) OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Длительность, мин") }, modifier = Modifier.fillMaxWidth())
            }
        }
        ids.forEach { id ->
            item(key = id) {
                FcCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        catalog.firstOrNull { it.id == id }?.let { ExerciseThumbnail(it, Modifier.width(92.dp).height(62.dp)) }
                        Text(catalog.firstOrNull { it.id == id }?.name ?: id, color = Fc.Text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    if (editing) {
                        rows.forEachIndexed { index, pair -> if (pair.first.exerciseId == id) {
                            SetRow(index, pair.second, true, onDone = { done -> rows[index] = pair.first to pair.second.copy(done = done) },
                                onRemove = { rows.removeAt(index) }, allowCompletedRemoval = true) { weight, reps -> rows[index] = pair.first to pair.second.copy(weight = weight, reps = reps) }
                        } }
                        ActionButton("+ Добавить подход", {
                            val old = rows.lastOrNull { it.first.exerciseId == id } ?: return@ActionButton
                            rows.add(old.first.copy(id = 0, setIndex = rows.size) to old.second.copy(done = true))
                        }, outlined = true)
                    } else {
                        val sets = log.sets.filter { it.exerciseId == id && it.done }
                        sets.forEachIndexed { index, set ->
                            Text("${index + 1}. ${set.weightKg} кг × ${set.reps}${if (set.isTimed) " сек." else " повт."}", color = Fc.Text, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        Text("Тоннаж: ${sets.filter { !it.isTimed }.sumOf { (it.weightKg * it.reps).toDouble() }.toInt()} кг", color = Fc.Lime)
                        val seconds = log.exercises.filter { it.exerciseId == id }.sumOf { it.durationMillis } / 1000
                        Text("Время упражнения: ${seconds / 60} мин ${seconds % 60} сек", color = Fc.Muted)
                    }
                }
            }
        }
        if (editing) {
            item { ActionButton("+ Добавить упражнение", { picker = true }, outlined = true) }
            item { ActionButton(if (vm.saving) "Сохраняю…" else "Сохранить изменения", { vm.save(log, rows.toList(), minutes.toInt()) { editing = false } }, enabled = valid && !vm.saving) }
        } else item { ActionButton("Редактировать результат", ::beginEditing) }
        if (vm.error != null) item { Text(vm.error.orEmpty(), color = Fc.Pink) }
    }
}
