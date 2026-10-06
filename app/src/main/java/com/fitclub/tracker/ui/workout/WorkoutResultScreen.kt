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
import com.fitclub.tracker.domain.LocalWeightUnits
import com.fitclub.tracker.domain.WeightUnits
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
    val settings = c.profile.settings
    val loaded = MutableStateFlow(false)
    val workouts = c.fitness.observeWorkouts().onEach { loaded.value = true }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val catalog = c.exercises.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val plans = c.schedule.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    fun save(log: WorkoutLog, rows: List<Pair<SetLogEntity, SetEntry>>, minutes: Int, units: WeightUnits, onSaved: () -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            runCatching {
                c.fitness.updateWorkout(log.session.id, log.session.startedAt,
                    if (minutes == log.durationMin) log.session.finishedAt ?: log.session.startedAt else log.session.startedAt + minutes * 60_000L,
                    rows.map { (old, row) -> old.copy(weightKg = if (row.weight == units.number(units.fromKg(old.weightKg))) old.weightKg else units.toKg(row.weight.replace(',', '.').toFloat()), reps = row.reps.toInt(), done = row.done, warmup = row.warmup, rir = row.rir.toIntOrNull(), assisted = row.assisted) })
            }.onSuccess { error = null; onSaved() }.onFailure { error = "Не удалось сохранить: ${it.message}" }
            saving = false
        }
    }
}

@Composable
fun WorkoutResultScreen(sessionId: Long, onClose: () -> Unit) {
    val vm = appViewModel(key = "result-$sessionId") { WorkoutResultViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val units = WeightUnits(settings.unitsKg)
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()
    val log = workouts.firstOrNull { it.session.id == sessionId }
    if (log == null) {
        Column(Modifier.padding(20.dp)) { BackRow("Календарь", onClose); Text(if (loaded) "Результат не найден или удалён" else "Загружаю результат…", color = Fc.Muted) }
        return
    }
    var editing by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var minutes by remember { mutableStateOf(log.durationMin.toString()) }
    var editKg by remember { mutableStateOf(settings.unitsKg) }
    val editUnits = WeightUnits(editKg)
    val rows = remember { mutableStateListOf<Pair<SetLogEntity, SetEntry>>() }
    fun beginEditing() {
        editKg = settings.unitsKg
        rows.clear(); rows.addAll(log.sets.map { it to SetEntry(units.number(units.fromKg(it.weightKg)), it.reps.toString(), it.done, it.warmup, it.rir?.toString().orEmpty(), it.assisted) })
        minutes = log.durationMin.toString(); editing = true
    }
    val close = { if (editing) discard = true else onClose() }
    BackHandler { if (picker) picker = false else close() }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Отменить изменения?") },
        confirmButton = { TextButton(onClick = { editing = false; discard = false }) { Text("Отменить изменения") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Продолжить редактирование") } })
    if (picker) {
        ExercisePicker(catalog, emptySet(), "Добавить подход", onBack = { picker = false }, onPick = { id ->
            val timed = id == "plank" || log.sets.firstOrNull { it.exerciseId == id }?.isTimed == true
            rows.add(SetLogEntity(sessionId = sessionId, exerciseId = id, setIndex = rows.size,
                weightKg = 0f, reps = if (timed) 45 else 8, done = true, isTimed = timed, exercisePosition = (rows.maxOfOrNull { it.first.exercisePosition } ?: -1) + 1) to SetEntry("0", if (timed) "45" else "8", true))
            picker = false
        })
        return
    }
    val valid = minutes.toIntOrNull()?.let { it in 1..1440 } == true && rows.all { (_, row) -> validSet(row) }
    val groups = if (editing) rows.map { it.first.exerciseId to it.first.exercisePosition }.distinct() else
        (log.sets.map { it.exerciseId to it.exercisePosition } + log.exercises.filter { ex -> log.sets.none { it.exerciseId == ex.exerciseId && it.exercisePosition == -1 } }.map { it.exerciseId to it.position }).distinct().sortedBy { it.second }
    val volume = if (editing) rows.filter { !it.first.isTimed && !it.second.assisted && it.second.done && validSet(it.second) }
        .sumOf { editUnits.toKg(it.second.weight.replace(',', '.').toFloat()).toDouble() * it.second.reps.toInt() } else log.volumeKg.toDouble()
    val count = if (editing) rows.count { it.second.done } else log.completedSets
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BackRow("Календарь", close) }
        item {
            Text(calendarDate(log.session.epochDay), color = Fc.Muted)
            ScreenTitle(if (editing) "Редактирование" else "Результат тренировки")
            Text(log.session.title.ifBlank { "Тренировка" }, color = Fc.Text)
            Text(log.status, color = Fc.Muted)
            log.session.plannedEpochDay?.takeIf { it != log.session.epochDay }?.let { Text("Была запланирована на ${calendarDate(it)}", color = Fc.Muted) }
        }
        item {
            FcCard {
                Text("Общий тоннаж: ${(if (editing) editUnits else units).format(volume.toFloat())}", color = Fc.Lime, style = MaterialTheme.typography.titleMedium)
                Text("$count подходов · ${if (editing) minutes else log.durationMin.toString()} мин", color = Fc.Muted)
                if (editing) OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Длительность, мин") }, modifier = Modifier.fillMaxWidth())
            }
        }
        groups.forEach { (id, position) ->
            item(key = "$id-$position") {
                FcCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        catalog.firstOrNull { it.id == id }?.let { ExerciseThumbnail(it, Modifier.width(92.dp).height(62.dp)) }
                        Text(catalog.firstOrNull { it.id == id }?.name ?: id, color = Fc.Text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    if (editing) {
                        Text("Вес, ${editUnits.label} · ${if (rows.firstOrNull { it.first.exerciseId == id && it.first.exercisePosition == position }?.first?.isTimed == true) "время, сек." else "повторы"}", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                        rows.forEachIndexed { index, pair -> if (pair.first.exerciseId == id && pair.first.exercisePosition == position) {
                            SetRow(index, pair.second, true, onDone = { done -> rows[index] = pair.first to pair.second.copy(done = done) },
                                onRemove = { rows.removeAt(index) }, allowCompletedRemoval = true) { weight, reps -> rows[index] = pair.first to pair.second.copy(weight = weight, reps = reps) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(pair.second.warmup, { rows[index] = pair.first to pair.second.copy(warmup = it) })
                                Text("Разминка", color = Fc.Muted)
                                Checkbox(pair.second.assisted, { rows[index] = pair.first to pair.second.copy(assisted = it) })
                                Text("Помощь", color = Fc.Muted)
                            }
                        } }
                        Segmented(listOf(false, true), rows.firstOrNull { it.first.exerciseId == id && it.first.exercisePosition == position }?.first?.isTimed ?: false,
                            { timed -> rows.indices.forEach { i -> if (rows[i].first.exerciseId == id && rows[i].first.exercisePosition == position) rows[i] = rows[i].first.copy(isTimed = timed) to rows[i].second } }, { if (it) "Секунды" else "Повторы" })
                        ActionButton("+ Добавить подход", {
                            val old = rows.lastOrNull { it.first.exerciseId == id && it.first.exercisePosition == position } ?: return@ActionButton
                            rows.add(old.first.copy(id = 0, setIndex = rows.size) to old.second.copy(done = true))
                        }, outlined = true)
                    } else {
                        val sets = log.sets.filter { it.exerciseId == id && it.exercisePosition == position && it.done }
                        sets.forEachIndexed { index, set ->
                            Text("${index + 1}. ${units.format(set.weightKg)} × ${set.reps}${if (set.isTimed) " сек." else " повт."}${if (set.warmup) " · разминка" else ""}${if (set.assisted) " · помощь" else ""}${set.rir?.let { " · RIR $it" }.orEmpty()}", color = Fc.Text, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        Text("Тоннаж: ${units.format(sets.filter { !it.isTimed && !it.assisted }.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat())}", color = Fc.Lime)
                        val seconds = log.exercises.filter { it.exerciseId == id && (position == -1 || it.position == position) }.sumOf { it.durationMillis } / 1000
                        val best = sets.filter { !it.warmup && !it.assisted }.maxWithOrNull(compareBy<SetLogEntity> { if (it.isTimed) it.reps.toFloat() else it.weightKg }.thenBy { it.reps })
                        if (best != null) Text(if (best.isTimed) "Самое долгое удержание: ${best.reps} сек." else "Самый тяжёлый: ${units.format(best.weightKg)} × ${best.reps}", color = Fc.Text)
                        val previous = workouts.filter { it.session.startedAt < log.session.startedAt }.flatMap { it.sets }
                            .filter { it.done && !it.warmup && !it.assisted && it.exerciseId == id && it.isTimed == best?.isTimed }
                            .maxWithOrNull(compareBy<SetLogEntity> { if (it.isTimed) it.reps.toFloat() else it.weightKg }.thenBy { it.reps })
                        Text(previous?.let { if (it.isTimed) "Рекорд до тренировки: ${it.reps} сек." else "Рекорд до тренировки: ${units.format(it.weightKg)} × ${it.reps}" } ?: "Первый результат в истории", color = Fc.Muted)
                        Text("Время упражнения: ${seconds / 60} мин ${seconds % 60} сек", color = Fc.Muted)
                    }
                }
            }
        }
        if (editing) {
            if (!valid) item { Text("Проверьте вес (от 0), количество (больше 0) и длительность (1–1440 минут).", color = Fc.Pink) }
            item { Text("Единицы ввода: ${editUnits.label}", color = Fc.Muted) }
            item { ActionButton("+ Добавить упражнение", { picker = true }, outlined = true) }
            item { ActionButton(if (vm.saving) "Сохраняю…" else "Сохранить изменения", { vm.save(log, rows.toList(), minutes.toInt(), editUnits) { editing = false } }, enabled = valid && !vm.saving) }
        } else item { ActionButton("Редактировать результат", ::beginEditing) }
        if (vm.error != null) item { Text(vm.error.orEmpty(), color = Fc.Pink) }
    }
}

