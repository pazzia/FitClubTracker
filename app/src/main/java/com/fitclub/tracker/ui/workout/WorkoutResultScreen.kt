package com.fitclub.tracker.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import com.fitclub.tracker.domain.Technique
import com.fitclub.tracker.domain.parseStages
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
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
import com.fitclub.tracker.domain.bestWorkingSet
import com.fitclub.tracker.AppContainer
import com.fitclub.tracker.data.db.SetLogEntity
import com.fitclub.tracker.data.repo.*
import com.fitclub.tracker.ui.components.*
import com.fitclub.tracker.ui.dashboard.calendarDate
import com.fitclub.tracker.ui.navigation.appViewModel
import com.fitclub.tracker.ui.programs.ExercisePicker
import com.fitclub.tracker.ui.theme.Fc
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.saveable.rememberSaveable
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
                    rows.map { (old, row) -> old.copy(weightKg = resultWeight(old, row, units), reps = row.reps.toInt(), done = row.done, warmup = row.warmup, rir = row.rir.toIntOrNull(), assisted = row.assisted) })
            }.onSuccess { error = null; onSaved() }.onFailure { error = "Не удалось сохранить: ${it.message}" }
            saving = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutResultScreen(sessionId: Long, onClose: () -> Unit, celebrate: Boolean = false, onProgress: () -> Unit = onClose) {
    val vm = appViewModel(key = "result-$sessionId") { WorkoutResultViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val units = WeightUnits(settings.unitsKg)
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()
    val log = workouts.firstOrNull { it.session.id == sessionId }
    if (log == null) {
        Column(Modifier.padding(20.dp)) { BackRow("Назад", onClose); Text(if (loaded) "Результат не найден или удалён" else "Загружаю результат…", color = Fc.Muted) }
        return
    }
    var editing by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Pair<String, Int>?>(null) }
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
    detail?.let { (id, position) ->
        ModalBottomSheet(onDismissRequest = { detail = null }, containerColor = Fc.Surface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            ExerciseResultSheet(log, id, position, catalog.firstOrNull { it.id == id }, workouts, units, vm.saving,
                onSave = { edited ->
                    val others = log.sets.filterNot { it.exerciseId == id && it.exercisePosition == position }.map { set ->
                        set to SetEntry(units.number(units.fromKg(set.weightKg)), set.reps.toString(), set.done, set.warmup, set.rir?.toString().orEmpty(), set.assisted)
                    }
                    vm.save(log, others + edited, log.durationMin, units) { detail = null }
                })
        }
    }
    val groups = if (editing) rows.map { it.first.exerciseId to it.first.exercisePosition }.distinct() else
        (log.sets.map { it.exerciseId to it.exercisePosition } + log.exercises.filter { ex -> log.sets.none { it.exerciseId == ex.exerciseId && it.exercisePosition == -1 } }.map { it.exerciseId to it.position }).distinct().sortedBy { it.second }
    val volume = if (editing) rows.filter { !it.first.isTimed && !it.second.assisted && it.second.done && validSet(it.second) }
        .sumOf { resultWeight(it.first, it.second, editUnits).toDouble() * it.second.reps.toInt() } else log.volumeKg.toDouble()
    val count = if (editing) rows.count { it.second.done } else log.completedSets
    var actionsReady by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("result-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (celebrate && !editing) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RoundIconButton("Закрыть", LineIcons.BACK, close)
                }
            }
            item {
                val today = java.time.LocalDate.now()
                val monday = com.fitclub.tracker.domain.weekStart(today).toEpochDay()
                val doneDays = workouts.filter { it.completedSets > 0 }.map { it.session.epochDay }.toSet()
                val name = settings.name.takeIf { it.isNotBlank() && it != "Мой профиль" }?.split(" ")?.first()
                val day = log.session.dayPosition
                WorkoutCompleteHeader(
                    name = name,
                    subtitle = if (day != null && log.session.programId != null) "День ${'A' + day} программы закрыт" else log.session.title.ifBlank { "Тренировка" },
                    minutes = log.durationMin,
                    exercises = log.sets.filter { it.done }.map { it.exerciseId to it.exercisePosition }.distinct().size,
                    volume = units.fromKg(log.volumeKg),
                    volumeLabel = units.label,
                    formatVolume = { v -> units.number(kotlin.math.round(v)) },
                    streakWeeks = com.fitclub.tracker.domain.weekStreak(workouts, today).coerceAtLeast(1),
                    weekDays = (0..6).map { monday + it in doneDays },
                    soundOn = settings.workoutSounds,
                    onFinished = { actionsReady = true }
                )
            }
            item {
                // Actions appear once the completion animation has played (WORKOUT_COMPLETE_ANIMATION_FINISHED).
                val actionsAlpha by androidx.compose.animation.core.animateFloatAsState(if (actionsReady) 1f else 0f,
                    androidx.compose.animation.core.tween(com.fitclub.tracker.ui.theme.FcMotion.MEDIUM_MS), label = "result-actions")
                Column(Modifier.graphicsLayer { alpha = actionsAlpha }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Смотреть прогресс", onProgress, Modifier.testTag("result-progress"), icon = PackIcons.BARS, enabled = actionsReady)
                    PillButton("На главную", onClose, Modifier.testTag("result-home"), kind = PillKind.SECONDARY, icon = PackIcons.HOME, enabled = actionsReady, height = 52.dp)
                }
            }
            item { SectionHeader("Подробности", Modifier.padding(top = 10.dp)) }
        } else {
        item { BackRow("Назад", close) }
        item {
            Eyebrow(calendarDate(log.session.epochDay))
            ScreenTitle(if (editing) "Редактирование" else "Результат тренировки", Modifier.padding(top = 4.dp))
            Text(log.session.title.ifBlank { "Тренировка" }, color = Fc.Text, style = MaterialTheme.typography.titleMedium)
            Pill(log.status, if (log.completedSets > 0) Fc.LimeSoft else Fc.Surface2, if (log.completedSets > 0) Fc.Lime else Fc.Muted, Modifier.padding(top = 8.dp))
            log.session.plannedEpochDay?.takeIf { it != log.session.epochDay }?.let { Text("Была запланирована на ${calendarDate(it)}", color = Fc.Muted, modifier = Modifier.padding(top = 6.dp)) }
        }
        item {
            StatRow(listOf(
                StatItem(if (editing) minutes else log.durationMin.toString(), "минут", PackIcons.STOPWATCH),
                StatItem((if (editing) editUnits else units).number((if (editing) editUnits else units).fromKg(volume.toFloat())), "${(if (editing) editUnits else units).label} объём", PackIcons.PLATES),
                StatItem("$count", com.fitclub.tracker.ui.programs.pluralRu(count, "подход", "подхода", "подходов"), PackIcons.DUMBBELL)
            ))
        }
        }
        if (!editing) {
            val earlier = workouts.filter { it.session.startedAt < log.session.startedAt }.flatMap { it.sets }
            val records = log.sets.filter { it.done && !it.warmup && !it.assisted }.map { it.exerciseId to it.isTimed }.distinct().mapNotNull { (id, timed) ->
                val best = bestWorkingSet(log.sets, id, timed) ?: return@mapNotNull null
                val previous = bestWorkingSet(earlier, id, timed) ?: return@mapNotNull null
                val better = if (timed) best.reps > previous.reps else best.weightKg > previous.weightKg || (best.weightKg == previous.weightKg && best.reps > previous.reps)
                if (!better) null else Triple(catalog.firstOrNull { it.id == id }?.name ?: id,
                    if (timed) "${best.reps} сек." else "${units.format(best.weightKg)} × ${best.reps}",
                    if (timed) "+${best.reps - previous.reps} сек." else if (best.weightKg > previous.weightKg) "+${units.format(best.weightKg - previous.weightKg)}" else "+${best.reps - previous.reps} повт.")
            }
            if (records.isNotEmpty()) item { RecordHero(records) }
        }
        item {
            FcCard(Modifier.fillMaxWidth()) {
                Text("Общий тоннаж: ${(if (editing) editUnits else units).format(volume.toFloat())}", color = Fc.Lime, style = MaterialTheme.typography.titleMedium)
                Text("$count ${com.fitclub.tracker.ui.programs.pluralRu(count, "подход", "подхода", "подходов")} · ${if (editing) minutes else log.durationMin.toString()} мин", color = Fc.Muted)
                if (editing) OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Длительность, мин") }, modifier = Modifier.fillMaxWidth())
            }
        }
        groups.forEach { (id, position) ->
            item(key = "$id-$position") {
                FcCard(Modifier.fillMaxWidth().testTag("result-exercise-$id-$position"), onClick = if (editing) null else ({ detail = id to position })) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        catalog.firstOrNull { it.id == id }?.let { ExerciseThumbnail(it, Modifier.width(92.dp).height(62.dp)) }
                        Text(catalog.firstOrNull { it.id == id }?.name ?: id, color = Fc.Text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (!editing) LineIcon(LineIcons.NEXT, Fc.Dim, Modifier.size(18.dp))
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
                        val earlierSets = workouts.filter { it.session.startedAt < log.session.startedAt }.flatMap { it.sets }
                        listOf(false, true).forEach { timed ->
                            val best = bestWorkingSet(sets, id, timed)
                            if (best != null) {
                                Text(if (timed) "Самое долгое удержание: ${best.reps} сек." else "Самый тяжёлый: ${units.format(best.weightKg)} × ${best.reps}", color = Fc.Text)
                                val previous = bestWorkingSet(earlierSets, id, timed)
                                Text(previous?.let { if (timed) "Рекорд до тренировки: ${it.reps} сек." else "Рекорд до тренировки: ${units.format(it.weightKg)} × ${it.reps}" }
                                    ?: if (timed) "Первый результат на время в истории" else "Первый результат с повторениями в истории", color = Fc.Muted)
                            }
                        }
                        if (seconds > 0) Text("Время упражнения: ${seconds / 60} мин ${seconds % 60} сек", color = Fc.Muted)
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


/** Retain canonical precision when the user leaves the rounded display untouched. */
private fun resultWeight(old: SetLogEntity, row: SetEntry, units: WeightUnits): Float =
    if (row.weight == units.number(units.fromKg(old.weightKg))) old.weightKg
    else units.toKg(row.weight.replace(',', '.').toFloat())

/** One exercise of a finished workout: every set with its technique, records, and in-place editing. */
@Composable
private fun ExerciseResultSheet(log: WorkoutLog, id: String, position: Int, exercise: com.fitclub.tracker.data.db.ExerciseEntity?,
    workouts: List<WorkoutLog>, units: WeightUnits, saving: Boolean, onSave: (List<Pair<SetLogEntity, SetEntry>>) -> Unit) {
    val sets = log.sets.filter { it.exerciseId == id && it.exercisePosition == position }
    var editing by remember { mutableStateOf(false) }
    val rows = remember { mutableStateListOf<Pair<SetLogEntity, SetEntry>>() }
    fun begin() {
        rows.clear()
        rows.addAll(sets.map { it to SetEntry(units.number(units.fromKg(it.weightKg)), it.reps.toString(), it.done, it.warmup, it.rir?.toString().orEmpty(), it.assisted) })
        editing = true
    }
    val earlier = workouts.filter { it.session.startedAt < log.session.startedAt }.flatMap { it.sets }
    val timed = sets.firstOrNull()?.isTimed == true
    val seconds = log.exercises.filter { it.exerciseId == id && (position == -1 || it.position == position) }.sumOf { it.durationMillis } / 1000
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            exercise?.let { ExerciseThumbnail(it, Modifier.size(64.dp)) }
            Column(Modifier.weight(1f)) {
                Eyebrow(calendarDate(log.session.epochDay))
                Text(exercise?.name ?: id, color = Fc.Text, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val done = sets.filter { it.done }
            listOf("${done.size}" to com.fitclub.tracker.ui.programs.pluralRu(done.size, "подход", "подхода", "подходов"),
                (if (timed) "${done.sumOf { it.reps }} с" else units.number(units.fromKg(done.filter { !it.assisted && !it.isTimed }.sumOf { com.fitclub.tracker.domain.setVolumeKg(it).toDouble() }.toFloat()))) to (if (timed) "под нагрузкой" else "${units.label} объём"),
                "${seconds / 60}:${"%02d".format(seconds % 60)}" to "время").forEach { (value, label) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Fc.Surface2).padding(12.dp)) {
                    NumberText(value, 22.sp)
                    Text(label, color = Fc.Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        listOf(false, true).forEach { t ->
            val best = bestWorkingSet(sets, id, t) ?: return@forEach
            val previous = bestWorkingSet(earlier, id, t)
            val record = previous == null || (if (t) best.reps > previous.reps else best.weightKg > previous.weightKg || (best.weightKg == previous.weightKg && best.reps > previous.reps))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (record) Fc.LimeSoft else Fc.Surface2).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineIcon(LineIcons.TROPHY, if (record) Fc.Lime else Fc.Muted)
                Column(Modifier.weight(1f)) {
                    Text(if (t) "Самое долгое удержание: ${best.reps} сек." else "Самый тяжёлый: ${units.format(best.weightKg)} × ${best.reps}", color = Fc.Text, style = MaterialTheme.typography.bodyMedium)
                    Text(previous?.let { if (t) "Рекорд до тренировки: ${it.reps} сек." else "Рекорд до тренировки: ${units.format(it.weightKg)} × ${it.reps}" }
                        ?: if (t) "Первый результат на время в истории" else "Первый результат с повторениями в истории", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                }
                if (record && previous != null) Pill("Рекорд", Fc.Lime, Fc.OnAccent)
            }
        }
        Eyebrow(if (editing) "Редактирование · ${units.label}" else "Подходы")
        if (!editing) sets.forEachIndexed { i, set ->
            val technique = Technique.of(set.technique)
            val stages = parseStages(set.stages)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Fc.Line2, RoundedCornerShape(14.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberText(if (set.warmup) "Р" else "${i + 1}", 16.sp, Modifier.width(22.dp), color = if (set.done) Fc.Lime else Fc.Muted)
                Column(Modifier.weight(1f)) {
                    NumberText((if (set.isTimed) "${set.reps} с" else "${units.format(set.weightKg)} × ${set.reps}") +
                        stages.joinToString("") { " → ${units.number(units.fromKg(it.weightKg))} × ${it.reps}" }, 19.sp)
                    val notes = listOfNotNull(technique.takeIf { it != Technique.NORMAL }?.title, "разминка".takeIf { set.warmup },
                        "помощь тренажёра".takeIf { set.assisted }, set.rir?.let { "в запасе $it" }, "не выполнен".takeIf { !set.done })
                    if (notes.isNotEmpty()) Text(notes.joinToString(" · "), color = Fc.Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            rows.forEachIndexed { index, pair ->
                SetRow(index, pair.second, true, onDone = { done -> rows[index] = pair.first to pair.second.copy(done = done) },
                    onRemove = { rows.removeAt(index) }, allowCompletedRemoval = true) { weight, reps -> rows[index] = pair.first to pair.second.copy(weight = weight, reps = reps) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(pair.second.warmup, { rows[index] = pair.first to pair.second.copy(warmup = it) })
                    Text("Разминка", color = Fc.Muted)
                    Checkbox(pair.second.assisted, { rows[index] = pair.first to pair.second.copy(assisted = it) })
                    Text("Помощь", color = Fc.Muted)
                }
            }
            ActionButton("+ Добавить подход", {
                val last = rows.lastOrNull() ?: return@ActionButton
                rows.add(last.first.copy(id = 0, setIndex = last.first.setIndex + 1, stages = "") to last.second.copy(done = true))
            }, outlined = true)
        }
        if (editing) {
            val valid = rows.all { validSet(it.second) }
            if (!valid) Text("Проверьте вес (от 0) и количество (больше 0).", color = Fc.Danger, style = MaterialTheme.typography.bodySmall)
            PrimaryButton(if (saving) "Сохраняю…" else "Сохранить", { if (valid && !saving) onSave(rows.toList()) }, Modifier.testTag("exercise-result-save"))
            ActionButton("Отменить", { editing = false }, outlined = true)
        } else ActionButton("Редактировать упражнение", ::begin, Modifier.testTag("exercise-result-edit"), outlined = true)
    }
}

/**
 * The one celebratory moment of the app: a new record gets the lime plate, the big number and a
 * short grow-in. Further records of the same workout are listed under it.
 */
@Composable
private fun RecordHero(records: List<Triple<String, String, String>>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val motion = remember {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    var shown by remember { mutableStateOf(!motion) }
    LaunchedEffect(Unit) { shown = true }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0.86f,
        androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 260f), label = "record")
    val (name, value, delta) = records.first()
    Column(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(24.dp)).background(com.fitclub.tracker.ui.theme.Lime).padding(20.dp)
            .semantics(mergeDescendants = true) {}.testTag("record-hero"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val ink = Color(0xFF0A0C0D)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineIcon(LineIcons.TROPHY, ink)
            Text(if (records.size == 1) "Новый рекорд" else "Новых рекордов: ${records.size}", color = ink, style = MaterialTheme.typography.titleMedium)
        }
        Text(name, color = ink, style = MaterialTheme.typography.bodyLarge)
        NumberText(value, 44.sp, color = ink)
        Text(delta, color = ink, style = MaterialTheme.typography.labelLarge)
        records.drop(1).forEach { (n, v, d) ->
            HorizontalDivider(color = ink.copy(alpha = .18f), modifier = Modifier.padding(vertical = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n, color = ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    NumberText(v, 18.sp, color = ink)
                    Text(d, color = ink, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
