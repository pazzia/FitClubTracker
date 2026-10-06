package com.fitclub.tracker.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.fitclub.tracker.ui.programs.ExercisePicker
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitclub.tracker.AppContainer
import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.model.RepRange
import com.fitclub.tracker.data.model.Sex
import com.fitclub.tracker.data.repo.*
import com.fitclub.tracker.ui.components.*
import com.fitclub.tracker.data.seed.FreeExerciseCatalog
import com.fitclub.tracker.ui.library.ExerciseMediaCard
import com.fitclub.tracker.ui.navigation.appViewModel
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

// Entries belong to positions, so two occurrences of an exercise never share rows.
data class WorkoutSource(val plan: ScheduledDraft? = null, val catalog: Map<String, ExerciseEntity> = emptyMap(),
    val unitsKg: Boolean = true, val sex: Sex = Sex.MALE, val loaded: Boolean = false)
data class SetEntry(val weight: String = "", val reps: String = "", val done: Boolean = false)

class WorkoutViewModel(private val c: AppContainer, scheduleId: Long) : ViewModel() {
    val source = combine(c.schedule.observe(scheduleId), c.exercises.observeAll(), c.profile.settings) { p, e, s ->
        WorkoutSource(p, e.associateBy { it.id }, s.unitsKg, s.sex, true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutSource())
    val history = c.fitness.observeWorkouts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var editedPlan by mutableStateOf<ScheduledDraft?>(null)
        private set
    var inOverview by mutableStateOf(true)
        private set
    val tokens = mutableStateListOf<Int>()
    private var nextToken = 0
    val started = mutableStateMapOf<Int, Boolean>()
    fun installPlan(plan: ScheduledDraft) {
        if (editedPlan == null) { editedPlan = plan; tokens.addAll(plan.items.map { nextToken++ }) }
    }
    fun overview() { pauseExercise(); inOverview = true }
    fun addExercise(id: String) {
        val p = editedPlan ?: return
        editedPlan = p.copy(items = p.items + ItemDraft(id, 3, RepRange.R8_12, 90))
        tokens.add(nextToken++)
    }
    fun reorder(from: Int, to: Int) {
        val p = editedPlan ?: return
        if (from == to || from !in p.items.indices || to !in p.items.indices) return
        val order = p.items.indices.toMutableList().apply { add(to, removeAt(from)) }
        remap(order)
        val oldTokens = tokens.toList(); tokens.clear(); tokens.addAll(order.map { oldTokens[it] })
        editedPlan = p.copy(items = order.map { p.items[it] })
    }
    fun removeExercise(index: Int) {
        val p = editedPlan ?: return
        val order = p.items.indices.filter { it != index }; remap(order); tokens.removeAt(index)
        editedPlan = p.copy(items = order.map { p.items[it] })
    }
    private fun remap(order: List<Int>) {
        pauseExercise()
        val oldLogs = logs.toMap(); val oldCompleted = completed.toMap(); val oldDurations = durations.toMap(); val oldStarted = started.toMap()
        logs.clear(); completed.clear(); durations.clear(); started.clear()
        order.forEachIndexed { new, old ->
            oldLogs[old]?.let { logs[new] = it }; oldCompleted[old]?.let { completed[new] = it }
            oldDurations[old]?.let { durations[new] = it }; oldStarted[old]?.let { started[new] = it }
        }
        exerciseIndex = order.indexOf(exerciseIndex).coerceAtLeast(0)
        summaryIndex = if (completed[exerciseIndex] == true) exerciseIndex else null
    }
    val startedAt = System.currentTimeMillis()
    var elapsed by mutableLongStateOf(0L)
    var exerciseIndex by mutableIntStateOf(0)
        private set
    var rest by mutableIntStateOf(0)
        private set
    private var restUntil = 0L
    val logs = mutableStateMapOf<Int, SnapshotStateList<SetEntry>>()
    val completed = mutableStateMapOf<Int, Boolean>()
    private val durations = mutableMapOf<Int, Long>()
    private var activeIndex: Int? = null
    private var activeSince = 0L
    var summaryIndex by mutableStateOf<Int?>(null)
        private set
    var finishing by mutableStateOf(false)
        private set
    private var saved = false
    var error by mutableStateOf<String?>(null)

    init { viewModelScope.launch { while (true) {
        val now = System.currentTimeMillis()
        elapsed = (now - startedAt) / 1000
        rest = ((restUntil - now) / 1000).toInt().coerceAtLeast(0)
        delay(1000)
    } } }
    fun startRest(seconds: Int) { restUntil = System.currentTimeMillis() + seconds * 1000L; rest = seconds }
    fun stopRest() { restUntil = 0; rest = 0 }
    fun startExercise() {
        if (activeIndex == null && summaryIndex == null) {
            started[exerciseIndex] = true; activeIndex = exerciseIndex; activeSince = System.currentTimeMillis()
        }
    }
    fun duration(index: Int): Long = (durations[index] ?: 0L) +
        if (activeIndex == index) (System.currentTimeMillis() - activeSince).coerceAtLeast(0) else 0L
    private fun pauseExercise() {
        activeIndex?.let { durations[it] = duration(it) }
        activeIndex = null
        stopRest()
    }
    fun moveTo(index: Int) {
        inOverview = false; pauseExercise(); exerciseIndex = index; summaryIndex = if (completed[index] == true) index else null
        startExercise()
    }
    fun completeExercise() {
        pauseExercise(); completed[exerciseIndex] = true; summaryIndex = exerciseIndex
    }
    fun editExercise() { completed.remove(exerciseIndex); summaryIndex = null; startExercise() }
    fun completedSets(plan: ScheduledDraft, unitsKg: Boolean): List<CompletedSet> {
        val counters = mutableMapOf<String, Int>()
        return plan.items.flatMapIndexed { position, item ->
            logs[position].orEmpty().filter { it.done }.map { row ->
                val index = counters[item.exerciseId] ?: 0
                counters[item.exerciseId] = index + 1
                CompletedSet(item.exerciseId, index, weightKg(row.weight, unitsKg), row.reps.toInt(), item.reps == RepRange.TIME)
            }
        }
    }
    fun finish(plan: ScheduledDraft, unitsKg: Boolean, onSaved: () -> Unit) {
        if (finishing || saved) return
        pauseExercise()
        val sets = completedSets(plan, unitsKg)
        val exercises = plan.items.mapIndexedNotNull { index, item ->
            if (durations.containsKey(index) || logs.containsKey(index))
                CompletedExercise(item.exerciseId, index, duration(index), completed[index] == true) else null
        }
        finishing = true
        viewModelScope.launch {
            runCatching {
                c.schedule.replaceItems(plan.id, plan.items)
                c.fitness.finishWorkout(plan.programId, plan.dayPosition, startedAt, sets, plan.id, plan.epochDay, exercises)
            }
                .onSuccess { saved = true; onSaved() }
                .onFailure { error = "Не удалось сохранить тренировку: ${it.message}"; startExercise() }
            finishing = false
        }
    }
}

@Composable
fun WorkoutScreen(scheduleId: Long, onClose: () -> Unit, onTechnique: (String) -> Unit) {
    val focus = LocalFocusManager.current
    val vm = appViewModel(key = "workout-$scheduleId") { WorkoutViewModel(it, scheduleId) }
    val source by vm.source.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    LaunchedEffect(source.plan?.id) { source.plan?.let(vm::installPlan) }
    val plan = vm.editedPlan ?: source.plan
    var confirmFinish by remember { mutableStateOf(false) }
    BackHandler { if (vm.inOverview) confirmFinish = true else vm.overview() }
    if (!source.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Fc.Lime) }
        return
    }
    if (plan == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("В этом дне пока нет упражнений", color = Fc.Text)
                TextButton(onClick = onClose) { Text("Вернуться к плану", color = Fc.Lime) }
            }
        }
        return
    }
    if (confirmFinish) AlertDialog(
        onDismissRequest = { if (!vm.finishing) confirmFinish = false },
        title = { Text("Завершить тренировку?") },
        text = { Text("Сохраним ${vm.completedSets(plan, source.unitsKg).size} выполненных подходов и время тренировки. Выполнять весь план не обязательно. Неотмеченные подходы не считаются выполненными.") },
        confirmButton = { TextButton(onClick = { vm.finish(plan, source.unitsKg, onClose) }, enabled = !vm.finishing) { Text(if (vm.finishing) "Сохраняю…" else "Завершить и сохранить") } },
        dismissButton = { TextButton(onClick = { confirmFinish = false }, enabled = !vm.finishing) { Text("Продолжить тренировку") } }
    )

    if (vm.inOverview || plan.items.isEmpty()) {
        WorkoutOverview(vm, plan, source, onOpen = vm::moveTo, onFinish = { confirmFinish = true })
        return
    }
    val index = vm.exerciseIndex.coerceIn(0, plan.items.lastIndex)
    val item = plan.items[index]
    val exercise = source.catalog[item.exerciseId]
    val rows = vm.logs.getOrPut(index) {
        mutableStateListOf<SetEntry>().apply { repeat(item.sets) { add(SetEntry(suggestedWeight(item.exerciseId), if (item.reps == RepRange.TIME) "45" else "8")) } }
    }
    LaunchedEffect(plan.id, index) { vm.startExercise() }
    val units = if (source.unitsKg) "кг" else "фунт."
    val doneRows = rows.filter { it.done }
    val heaviest = doneRows.maxWithOrNull(compareBy<SetEntry> { weightKg(it.weight, source.unitsKg) }.thenBy { it.reps.toIntOrNull() ?: 0 })
    val previousBest = history.filter { it.session.startedAt < vm.startedAt }.flatMap { it.sets }
        .filter { it.done && it.exerciseId == item.exerciseId && it.isTimed == (item.reps == RepRange.TIME) }
        .maxWithOrNull(compareBy<com.fitclub.tracker.data.db.SetLogEntity> { it.weightKg }.thenBy { it.reps })
    val volume = if (item.reps == RepRange.TIME) 0f else doneRows.sumOf { (weightKg(it.weight, source.unitsKg) * it.reps.toInt()).toDouble() }.toFloat()
    val summary = vm.summaryIndex != null
    val listState = rememberLazyListState()
    LaunchedEffect(index, summary) { listState.scrollToItem(0) }


    Column(Modifier.fillMaxSize().background(Fc.Bg)) {
        Row(Modifier.fillMaxWidth().background(Fc.Nav).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(plan.title, style = MaterialTheme.typography.titleSmall, color = Fc.Text); Text(time(vm.elapsed), color = Fc.Lime) }
            TextButton(onClick = { focus.clearFocus(); confirmFinish = true }, enabled = !vm.finishing, modifier = Modifier.testTag("finish-workout")) { Text("Завершить", color = Fc.Lime) }
        }
        LinearProgressIndicator(progress = { vm.completed.count { it.value }.toFloat() / plan.items.size }, modifier = Modifier.fillMaxWidth(), color = Fc.Lime, trackColor = Fc.Surface2)
        LazyColumn(Modifier.weight(1f).testTag("workout-list"), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("УПРАЖНЕНИЕ ${index + 1} ИЗ ${plan.items.size}", color = Fc.Muted, style = MaterialTheme.typography.labelMedium)
                Text(exercise?.name ?: item.exerciseId, color = Fc.Text, style = MaterialTheme.typography.displaySmall)
            }
            if (!summary && exercise != null) FreeExerciseCatalog.media[exercise.id]?.let { media ->
                item { ExerciseMediaCard(exercise.id, exercise.name, media, showVideo = false) }
            }
            if (summary) {
                item {
                    FcCard(border = Fc.Lime.copy(.45f)) {
                        Text("Упражнение завершено", color = Fc.Lime, style = MaterialTheme.typography.titleMedium)
                        Text("Подходов: ${doneRows.size}", color = Fc.Text)
                        if (item.reps == RepRange.TIME) Text("Выполнено: ${doneRows.sumOf { it.reps.toInt() }} сек.", color = Fc.Text)
                        else Text("Тоннаж: ${number(displayWeight(volume, source.unitsKg))} $units", color = Fc.Text)
                        Text("Время упражнения: ${time(vm.duration(index) / 1000)}", color = Fc.Text)
                        Text("Самый тяжёлый подход: " + (heaviest?.let { "${number(displayWeight(weightKg(it.weight, source.unitsKg), source.unitsKg))} $units × ${it.reps}${if (item.reps == RepRange.TIME) " сек." else ""}" } ?: "нет выполненных подходов"), color = Fc.Text)
                        Text("Рекорд за всю историю до этой тренировки: " + (previousBest?.let { "${number(displayWeight(it.weightKg, source.unitsKg))} $units × ${it.reps}${if (it.isTimed) " сек." else ""}" } ?: "первый результат"), color = Fc.Muted)
                        if (heaviest != null && previousBest != null) {
                            val current = weightKg(heaviest.weight, source.unitsKg)
                            val delta = displayWeight(current - previousBest.weightKg, source.unitsKg)
                            Text("Сравнение по весу: ${if (delta > 0) "+" else ""}${number(delta)} $units", color = if (delta > 0) Fc.Lime else Fc.Text)
                            if (current == previousBest.weightKg) Text("При том же весе: ${heaviest.reps.toInt() - previousBest.reps} ${if (item.reps == RepRange.TIME) "сек." else "повт."}", color = Fc.Muted)
                        }
                        TextButton(onClick = { vm.editExercise() }) { Text("Изменить подходы", color = Fc.Lime) }
                    }
                }
            } else {
                if (item.comment.isNotBlank()) item { FcCard { Text("Комментарий к упражнению", color = Fc.Lime); Text(item.comment, color = Fc.Text) } }
                item { TextButton(onClick = { onTechnique(item.exerciseId) }, contentPadding = PaddingValues(0.dp)) { Text("Техника выполнения →", color = Fc.Lime) } }
                item {
                    FcCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("№", color = Fc.Muted, textAlign = TextAlign.Center, modifier = Modifier.width(26.dp))
                            Text(units, color = Fc.Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Text(if (item.reps == RepRange.TIME) "сек." else "повт.", color = Fc.Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(80.dp))
                        }
                        rows.forEachIndexed { rowIndex, row ->
                            SetRow(rowIndex, row, canRemove = rowIndex >= item.sets, onDone = { checked ->
                                if (!checked || validSet(row)) {
                                    rows[rowIndex] = row.copy(done = checked); vm.error = null
                                    if (checked) vm.startRest(item.restSec)
                                } else vm.error = "Укажите вес не меньше 0 и количество больше 0"
                            }, onRemove = { rows.removeAt(rowIndex) }) { w, r -> rows[rowIndex] = row.copy(weight = w, reps = r, done = false) }
                        }
                        TextButton(onClick = { rows.add(SetEntry(weight = rows.lastOrNull()?.weight ?: suggestedWeight(item.exerciseId))) }, modifier = Modifier.fillMaxWidth()) { Text("+ Добавить подход", color = Fc.Lime) }
                    }
                }
                if (vm.rest > 0) item { FcCard { Text("Отдых: ${time(vm.rest.toLong())}", color = Fc.Lime); TextButton(onClick = vm::stopRest) { Text("Пропустить отдых", color = Fc.Lime) } } }
            }
            item {
                plan.items.getOrNull(index + 1)?.let { next -> FcCard {
                    Text("СЛЕДУЮЩЕЕ УПРАЖНЕНИЕ", color = Fc.Muted, style = MaterialTheme.typography.labelMedium)
                    Text(source.catalog[next.exerciseId]?.name ?: next.exerciseId, color = Fc.Text)
                } }
            }
        }
        if (vm.error != null) Text(vm.error.orEmpty(), color = Fc.Pink, modifier = Modifier.padding(16.dp))
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!summary) Button(onClick = { focus.clearFocus(); vm.completeExercise() }, enabled = !vm.finishing,
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("complete-exercise"), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Fc.Lime)) { Text("Завершить упражнение", color = Fc.Bg) }
            ActionButton("Все упражнения", { focus.clearFocus(); vm.overview() }, outlined = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Назад", { focus.clearFocus(); vm.moveTo(index - 1) }, modifier = Modifier.weight(1f), outlined = true, enabled = index > 0 && !vm.finishing)
                ActionButton(if (index == plan.items.lastIndex) "Завершить тренировку" else "Далее →",
                    { focus.clearFocus(); if (index < plan.items.lastIndex) vm.moveTo(index + 1) else confirmFinish = true },
                    modifier = Modifier.weight(1f).testTag("next-exercise"), outlined = true, enabled = !vm.finishing)
            }
        }
    }
}

@Composable
internal fun SetRow(index: Int, row: SetEntry, canRemove: Boolean, onDone: (Boolean) -> Unit, onRemove: () -> Unit, allowCompletedRemoval: Boolean = false, onEdit: (String, String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${index + 1}", color = if (row.done) Fc.Lime else Fc.Text, textAlign = TextAlign.Center, modifier = Modifier.width(26.dp))
        OutlinedTextField(row.weight, { onEdit(it, row.reps) }, singleLine = true,
            modifier = Modifier.weight(1f).height(56.dp).testTag("weight-$index"), shape = RoundedCornerShape(10.dp), colors = fieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(row.reps, { onEdit(row.weight, it.filter(Char::isDigit)) }, singleLine = true,
            modifier = Modifier.weight(1f).height(56.dp).testTag("reps-$index"), shape = RoundedCornerShape(10.dp), colors = fieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        IconButton(onClick = { onDone(!row.done) }, modifier = Modifier.size(48.dp).testTag("done-$index")) {
            Icon(Icons.Filled.Check, "Подход ${index + 1} выполнен", tint = if (row.done) Fc.Bg else Fc.Muted,
                modifier = Modifier.background(if (row.done) Fc.Lime else Fc.Surface2, CircleShape).padding(5.dp))
        }
        // Reserve the same column for every row, including planned and completed sets.
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            if (canRemove && (!row.done || allowCompletedRemoval)) IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Delete, "Удалить подход ${index + 1}", tint = Fc.Muted) }
        }
    }
}

internal fun validSet(row: SetEntry): Boolean {
    val w = row.weight.replace(',', '.').toFloatOrNull()
    return w != null && w.isFinite() && w >= 0 && (row.reps.toIntOrNull() ?: 0) > 0
}
private fun weightKg(text: String, unitsKg: Boolean) = (text.replace(',', '.').toFloatOrNull() ?: 0f) / if (unitsKg) 1f else 2.2046226f
private fun displayWeight(kg: Float, unitsKg: Boolean) = kg * if (unitsKg) 1f else 2.2046226f
private fun number(value: Float) = String.format(Locale.US, "%.1f", value).removeSuffix(".0")
@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedTextColor = Fc.Text, unfocusedTextColor = Fc.Text, focusedBorderColor = Fc.Lime, unfocusedBorderColor = Fc.Line, cursorColor = Fc.Lime)
private fun time(sec: Long) = "%02d:%02d".format(Locale.US, sec / 60, sec % 60)
private fun suggestedWeight(id: String) = if (id in listOf("plank", "pushup", "pullup", "hangraise")) "0" else ""



@Composable
private fun WorkoutOverview(vm: WorkoutViewModel, plan: ScheduledDraft, source: WorkoutSource,
    onOpen: (Int) -> Unit, onFinish: () -> Unit) {
    var picker by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Int?>(null) }
    val list = rememberLazyListState()
    val threshold = with(LocalDensity.current) { 110.dp.toPx() }
    var dragging by remember { mutableStateOf<Int?>(null) }
    if (picker) {
        ExercisePicker(source.catalog.values.sortedBy { it.name }, emptySet(), plan.title,
            onBack = { picker = false }, onPick = { vm.addExercise(it); picker = false })
        BackHandler { picker = false }
        return
    }
    if (removing != null) AlertDialog(onDismissRequest = { removing = null }, title = { Text("Убрать упражнение из тренировки?") },
        text = { Text("Введённые для него подходы будут удалены. Другие упражнения сохранятся.") },
        confirmButton = { TextButton(onClick = { removing?.let(vm::removeExercise); removing = null }) { Text("Убрать", color = Fc.Pink) } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text("Отмена") } })
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).testTag("workout-overview"), state = list,
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(plan.title, color = Fc.Text, style = MaterialTheme.typography.titleMedium)
                Text(time(vm.elapsed), color = Fc.Lime)
                ScreenTitle("План тренировки")
                Text("Выберите любое упражнение. Удерживайте ≡, чтобы изменить порядок.", color = Fc.Muted)
                val volume = vm.completedSets(plan, source.unitsKg).filter { !it.isTimed }.sumOf { (it.weightKg * it.reps).toDouble() }
                Text("Выполнено ${vm.completed.count { it.value }} из ${plan.items.size} · тоннаж ${volume.toInt()} кг", color = Fc.Lime, modifier = Modifier.padding(top = 8.dp))
            }
            itemsIndexed(plan.items, key = { i, _ -> vm.tokens.getOrNull(i) ?: i }) { index, item ->
                val token = vm.tokens.getOrNull(index) ?: index
                val done = vm.completed[index] == true
                val begun = vm.started[index] == true
                val exercise = source.catalog[item.exerciseId]
                FcCard(modifier = Modifier.fillMaxWidth(), border = if (done || begun || dragging == token) Fc.Lime else Fc.Line,
                    onClick = { onOpen(index) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        exercise?.let { ExerciseThumbnail(it, Modifier.width(92.dp).height(62.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(exercise?.name ?: item.exerciseId, color = Fc.Text, style = MaterialTheme.typography.titleSmall)
                            Text(if (done) "Завершено" else if (begun) "Начато · продолжить" else "${item.sets} × ${item.reps.label}", color = if (done || begun) Fc.Lime else Fc.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("≡", color = Fc.Muted, style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.size(48.dp).testTag("drag-exercise-$token").pointerInput(token) {
                                var delta = 0f
                                detectDragGesturesAfterLongPress(onDragStart = { dragging = token; delta = 0f },
                                    onDragEnd = { dragging = null }, onDragCancel = { dragging = null }) { change, amount ->
                                    change.consume(); delta += amount.y
                                    if (kotlin.math.abs(delta) >= threshold) {
                                        val from = vm.tokens.indexOf(token)
                                        val to = (from + if (delta > 0) 1 else -1).coerceIn(0, vm.tokens.lastIndex)
                                        vm.reorder(from, to); delta = 0f
                                    }
                                }
                            })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { if (index > 0) vm.reorder(index, index - 1) }, enabled = index > 0) { Text("↑") }
                        TextButton(onClick = { if (index < plan.items.lastIndex) vm.reorder(index, index + 1) }, enabled = index < plan.items.lastIndex) { Text("↓") }
                        TextButton(onClick = { removing = index }) { Text("Убрать", color = Fc.Muted) }
                    }
                }
            }
            item { ActionButton("+ Добавить упражнение", { picker = true }, outlined = true) }
            if (vm.error != null) item { Text(vm.error.orEmpty(), color = Fc.Pink) }
        }
        ActionButton("Завершить тренировку", onFinish, modifier = Modifier.padding(16.dp).testTag("finish-workout"), enabled = !vm.finishing)
    }
}
