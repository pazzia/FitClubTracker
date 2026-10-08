@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.fitclub.tracker.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.fitclub.tracker.data.db.SetLogEntity
import com.fitclub.tracker.domain.Technique
import com.fitclub.tracker.domain.assignSuperset
import com.fitclub.tracker.domain.supersetGroups
import com.fitclub.tracker.domain.supersetLabels
import com.fitclub.tracker.domain.TechniqueScope
import com.fitclub.tracker.domain.estimatedOneRepMax
import com.fitclub.tracker.domain.lastSessionSets
import com.fitclub.tracker.domain.setVolumeKg
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.sp
import com.fitclub.tracker.data.model.restLabel
import com.fitclub.tracker.ui.theme.NumberStyle
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
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import com.fitclub.tracker.domain.WeightUnits
import com.fitclub.tracker.domain.defaultCount
import com.fitclub.tracker.domain.previousWorkingSet
import com.fitclub.tracker.domain.bestWorkingSet
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.toggleableState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

// Entries belong to positions, so two occurrences of an exercise never share rows.
data class WorkoutSource(val plan: ScheduledDraft? = null, val catalog: Map<String, ExerciseEntity> = emptyMap(),
    val unitsKg: Boolean = true, val sex: Sex = Sex.MALE, val loaded: Boolean = false)
data class SetEntry(val weight: String = "", val reps: String = "", val done: Boolean = false, val warmup: Boolean = false, val rir: String = "", val assisted: Boolean = false,
    /** [com.fitclub.tracker.domain.Technique.id]. */
    val technique: String = "",
    /** Extra stages of a drop set or rest-pause, as typed: weight to reps. */
    val stages: List<Pair<String, String>> = emptyList())

/** How undone sets of an exercise are pre-filled. */
enum class Prefill { EMPTY, REPEAT, PROGRESS }



class WorkoutViewModel(private val c: AppContainer, private val scheduleId: Long) : ViewModel() {
    val source = combine(c.schedule.observe(scheduleId), c.exercises.observeAll(), c.profile.settings) { p, e, s ->
        WorkoutSource(p, e.associateBy { it.id }, s.unitsKg, s.sex, true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutSource())
    val history = c.fitness.observeWorkouts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var editedPlan by mutableStateOf<ScheduledDraft?>(null)
        private set
    // Opening a workout goes straight to the first exercise; the plan is one tap away.
    var inOverview by mutableStateOf(false)
        private set
    val tokens = mutableStateListOf<Int>()
    private var nextToken = 0
    val started = mutableStateMapOf<Int, Boolean>()
    fun installPlan(plan: ScheduledDraft) {
        if (editedPlan == null) { editedPlan = plan; tokens.addAll(plan.items.map { nextToken++ }) }
    }
    fun overview() { pauseExercise(); inOverview = true; error = null }
    /** Pre-fill mode chosen per exercise position. */
    val prefill = mutableStateMapOf<Int, Prefill>()
    fun setSuperset(index: Int, partner: Int?) {
        val p = editedPlan ?: return
        editedPlan = p.copy(items = assignSuperset(p.items, index, partner))
    }
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
    var restored by mutableStateOf(false)
        private set
    var draftUnitsKg by mutableStateOf(c.profile.settings.value.unitsKg)
        private set
    var startedAt = System.currentTimeMillis()
        private set
    private var elapsedBase = 0L
    private var sessionRealtime = android.os.SystemClock.elapsedRealtime()
    private var activeRealtime = 0L
    var elapsed by mutableLongStateOf(0L)
    var exerciseIndex by mutableIntStateOf(0)
        private set
    var rest by mutableIntStateOf(0)
        private set
    /** Length of the current rest, for the progress ring. */
    var restTotal by mutableIntStateOf(0)
        private set
    /** Increments each time a rest runs out on its own (not when skipped). */
    var restEnded by mutableIntStateOf(0)
        private set
    private var restUntil by mutableLongStateOf(0L)
    private var restRealtime = 0L
    val logs = mutableStateMapOf<Int, SnapshotStateList<SetEntry>>()
    val completed = mutableStateMapOf<Int, Boolean>()
    private val durations = mutableStateMapOf<Int, Long>()
    private var activeIndex by mutableStateOf<Int?>(null)
    private var activeSince by mutableLongStateOf(0L)
    var summaryIndex by mutableStateOf<Int?>(null)
        private set
    var finishing by mutableStateOf(false)
        private set
    private var saved = false
    var error by mutableStateOf<String?>(null)

    private var persistJob: Job? = null
    private fun startPersistence() {
        persistJob = viewModelScope.launch {
            snapshotFlow { if (!saved && !finishing && editedPlan != null) snapshot() else null }
                .filterNotNull().collect { value ->
                    runCatching { c.fitness.saveActive(scheduleId, editedPlan?.title.orEmpty(), value) }
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = "Не удалось сохранить черновик. Повторите сохранение перед выходом." }
                }
        }
    }
    internal fun snapshot(): String {
        val p = requireNotNull(editedPlan)
        val now = System.currentTimeMillis()
        val mono = android.os.SystemClock.elapsedRealtime()
        val result = JSONObject().put("savedAt", now).put("elapsedMs", elapsedBase + (mono - sessionRealtime).coerceAtLeast(0))
            .put("activeElapsed", if (activeIndex != null) (mono - activeRealtime).coerceAtLeast(0) else 0)
            .put("startedAt", startedAt).put("unitsKg", draftUnitsKg)
            .put("restUntil", if (restUntil == 0L) 0L else now + (restRealtime - mono).coerceAtLeast(0)).put("index", exerciseIndex).put("overview", inOverview)
            .put("summary", summaryIndex ?: -1).put("activeIndex", activeIndex ?: -1).put("activeSince", activeSince)
            .put("epochDay", p.epochDay).put("title", p.title).put("minute", p.startMinute)
            .put("program", p.programId ?: -1).put("day", p.dayPosition ?: -1)
        val items = JSONArray()
        p.items.forEachIndexed { index, item ->
            val rows = JSONArray()
            logs[index].orEmpty().forEach { rows.put(JSONObject().put("w", it.weight).put("r", it.reps).put("done", it.done).put("warmup", it.warmup).put("rir", it.rir).put("assisted", it.assisted)
                .put("tech", it.technique).put("stages", JSONArray().apply { it.stages.forEach { (w, r) -> put(JSONArray().put(w).put(r)) } })) }
            items.put(JSONObject().put("id", item.exerciseId).put("sets", item.sets).put("reps", item.reps.name)
                .put("rest", item.restSec).put("comment", item.comment).put("rows", rows)
                .put("technique", item.technique).put("scope", item.techniqueScope).put("superset", item.superset)
                .put("prefill", prefill[index]?.name ?: "")
                .put("completed", completed[index] == true).put("started", started[index] == true)
                .put("duration", durations[index] ?: 0L))
        }
        return result.put("items", items).toString()
    }
    internal fun restore(value: String) {
        val j = JSONObject(value); val items = j.getJSONArray("items")
        // Decode completely before touching live state; failed recovery never silently discards a draft.
        val restoredItems = (0 until items.length()).map { i -> val item = items.getJSONObject(i)
            ItemDraft(item.getString("id"), item.getInt("sets"), RepRange.valueOf(item.getString("reps")), item.getInt("rest"), item.optString("comment"),
                item.optString("technique"), item.optString("scope"), item.optInt("superset")) }
        val restoredRows = (0 until items.length()).map { i -> val rows = items.getJSONObject(i).getJSONArray("rows")
            (0 until rows.length()).map { r -> val row = rows.getJSONObject(r)
                val stages = row.optJSONArray("stages")?.let { a -> (0 until a.length()).map { k -> a.getJSONArray(k).let { it.getString(0) to it.getString(1) } } }.orEmpty()
                SetEntry(row.getString("w"), row.getString("r"), row.getBoolean("done"), row.optBoolean("warmup"), row.optString("rir"), row.optBoolean("assisted"), row.optString("tech"), stages) } }
        editedPlan = ScheduledDraft(scheduleId, j.getLong("epochDay"), j.getString("title"), j.getInt("minute"),
            j.getLong("program").takeIf { it >= 0 }, j.getInt("day").takeIf { it >= 0 }, restoredItems)
        tokens.clear(); tokens.addAll(restoredItems.map { nextToken++ }); logs.clear(); completed.clear(); started.clear(); durations.clear()
        restoredRows.forEachIndexed { i, rows -> logs[i] = mutableStateListOf<SetEntry>().apply { addAll(rows) }
            val item = items.getJSONObject(i); completed[i] = item.getBoolean("completed"); started[i] = item.getBoolean("started"); durations[i] = item.getLong("duration")
            item.optString("prefill").takeIf { it.isNotBlank() }?.let { name -> runCatching { Prefill.valueOf(name) }.getOrNull()?.let { prefill[i] = it } } }
        startedAt = j.getLong("startedAt"); elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0) / 1000; draftUnitsKg = j.getBoolean("unitsKg"); restUntil = j.getLong("restUntil"); restRealtime = if (restUntil == 0L) 0L else android.os.SystemClock.elapsedRealtime() + (restUntil - System.currentTimeMillis()).coerceAtLeast(0); restTotal = (((restRealtime - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0) + 999) / 1000).toInt()
        exerciseIndex = j.getInt("index"); inOverview = j.getBoolean("overview"); summaryIndex = j.getInt("summary").takeIf { it >= 0 }
        activeIndex = j.getInt("activeIndex").takeIf { it >= 0 }; activeSince = System.currentTimeMillis()
        val gap = (System.currentTimeMillis() - j.optLong("savedAt", System.currentTimeMillis())).coerceAtLeast(0)
        sessionRealtime = android.os.SystemClock.elapsedRealtime()
        elapsedBase = j.optLong("elapsedMs", (System.currentTimeMillis() - startedAt).coerceAtLeast(0)) + gap
        activeRealtime = sessionRealtime
        activeIndex?.let { durations[it] = (durations[it] ?: 0L) + j.optLong("activeElapsed", 0) + gap }
    }
    init {
        viewModelScope.launch {
            runCatching {
                c.fitness.getActive(scheduleId)?.let { restore(it.snapshot) }
                if (editedPlan == null) source.first { it.loaded }.plan?.let(::installPlan)
                restored = true; startPersistence()
            }.onFailure { error = "Не удалось восстановить тренировку: ${it.message}" }
        }
        viewModelScope.launch { while (true) {
        val now = System.currentTimeMillis()
        elapsed = (elapsedBase + (android.os.SystemClock.elapsedRealtime() - sessionRealtime).coerceAtLeast(0)) / 1000
        val remaining = if (restRealtime == 0L) 0 else ((restRealtime - android.os.SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
        if (rest > 0 && remaining == 0 && restRealtime != 0L) { restEnded++; restUntil = 0; restRealtime = 0 }
        rest = remaining
        delay(if (rest in 1..3) 250 else 1000)
    } }
    }
    fun startRest(seconds: Int) {
        if (seconds <= 0) { stopRest(); return }
        restUntil = System.currentTimeMillis() + seconds * 1000L; restRealtime = android.os.SystemClock.elapsedRealtime() + seconds * 1000L
        rest = seconds; restTotal = seconds
    }
    /** Lengthens or shortens the running rest; shortening past zero ends it. */
    fun addRest(seconds: Int) {
        if (restRealtime == 0L) return
        val left = rest + seconds
        if (left <= 0) { stopRest(); return }
        restUntil += seconds * 1000L; restRealtime += seconds * 1000L
        rest = left; restTotal = maxOf(restTotal + seconds, left)
    }
    fun stopRest() { restUntil = 0; restRealtime = 0; rest = 0 }
    fun startExercise() {
        if (activeIndex == null && summaryIndex == null) {
            started[exerciseIndex] = true; activeIndex = exerciseIndex; activeSince = System.currentTimeMillis(); activeRealtime = android.os.SystemClock.elapsedRealtime()
        }
    }
    fun duration(index: Int): Long = (durations[index] ?: 0L) +
        if (activeIndex == index) (android.os.SystemClock.elapsedRealtime() - activeRealtime).coerceAtLeast(0) else 0L
    private fun pauseExercise() {
        activeIndex?.let { durations[it] = duration(it) }
        activeIndex = null
    }
    fun moveTo(index: Int) {
        error = null; inOverview = false; pauseExercise(); exerciseIndex = index; summaryIndex = if (completed[index] == true) index else null
        startExercise()
    }
    fun completeExercise() {
        if (logs[exerciseIndex].orEmpty().none { it.done }) {
            error = "Отметьте выполненный подход или перейдите к другому упражнению, чтобы пропустить это."; return
        }
        pauseExercise(); completed[exerciseIndex] = logs[exerciseIndex].orEmpty().count { it.done && !it.warmup } >= (editedPlan?.items?.getOrNull(exerciseIndex)?.sets ?: 1); summaryIndex = exerciseIndex
    }
    fun editExercise() { completed.remove(exerciseIndex); summaryIndex = null; startExercise() }
    fun completedSets(plan: ScheduledDraft, unitsKg: Boolean): List<CompletedSet> {
        val counters = mutableMapOf<String, Int>()
        return plan.items.flatMapIndexed { position, item ->
            logs[position].orEmpty().filter { it.done }.map { row ->
                val index = counters[item.exerciseId] ?: 0
                counters[item.exerciseId] = index + 1
                val stages = row.stages.mapNotNull { (w, r) -> val reps = r.toIntOrNull(); if (reps != null && reps > 0) com.fitclub.tracker.domain.Stage(weightKg(w, draftUnitsKg), reps) else null }
                CompletedSet(item.exerciseId, index, weightKg(row.weight, draftUnitsKg), row.reps.toInt(), item.reps == RepRange.TIME, position, row.warmup, row.rir.toIntOrNull(), row.assisted,
                    row.technique, com.fitclub.tracker.domain.formatStages(stages))
            }
        }
    }
    internal fun completedExercises(plan: ScheduledDraft): List<CompletedExercise> = plan.items.mapIndexed { index, item ->
        CompletedExercise(item.exerciseId, index, duration(index), logs[index].orEmpty().count { it.done && !it.warmup } >= item.sets)
    }
    fun saveForLater(onSaved: () -> Unit) {
        if (finishing || saved) return
        pauseExercise(); inOverview = true; finishing = true
        viewModelScope.launch {
            runCatching { persistJob?.cancelAndJoin(); c.fitness.saveActive(scheduleId, editedPlan?.title.orEmpty(), snapshot()) }
                .onSuccess { onSaved() }.onFailure { error = "Не удалось сохранить черновик: ${it.message}" }
            finishing = false
            startPersistence()
        }
    }
    fun finish(plan: ScheduledDraft, unitsKg: Boolean, onSaved: (Long) -> Unit) {
        if (finishing || saved) return
        pauseExercise()
        val sets = completedSets(plan, unitsKg)
        val exercises = completedExercises(plan)
        finishing = true
        viewModelScope.launch {
            runCatching {
                persistJob?.cancelAndJoin()
                c.fitness.finishWorkout(plan.programId, plan.dayPosition, startedAt, sets, plan.id,
                    java.time.Instant.ofEpochMilli(startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay(), exercises,
                    title = plan.title, plannedEpochDay = plan.epochDay, planSnapshot = snapshot(),
                    elapsedMillis = elapsedBase + (android.os.SystemClock.elapsedRealtime() - sessionRealtime).coerceAtLeast(0))
            }
                .onSuccess { saved = true; onSaved(it) }
                .onFailure { error = "Не удалось сохранить тренировку: ${it.message}"; if (!inOverview) startExercise() }
            finishing = false
            if (!saved) startPersistence()
        }
    }
}

@Composable
fun WorkoutScreen(scheduleId: Long, onClose: () -> Unit, onTechnique: (String) -> Unit, onFinished: (Long) -> Unit = { onClose() }) {
    val focus = LocalFocusManager.current
    val vm = appViewModel(key = "workout-$scheduleId") { WorkoutViewModel(it, scheduleId) }
    val source by vm.source.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    val plan = vm.editedPlan ?: source.plan
    var confirmFinish by remember { mutableStateOf(false) }
    var restOpen by rememberSaveable { mutableStateOf(false) }
    KeepScreenOn()
    RestEndSignal(vm.restEnded)
    LaunchedEffect(vm.rest == 0) { if (vm.rest == 0) restOpen = false }
    BackHandler {
        if (restOpen) restOpen = false else if (vm.inOverview) confirmFinish = true else vm.overview()
    }
    if (!source.loaded || !vm.restored) {
        Box(Modifier.fillMaxSize().background(Fc.Bg), contentAlignment = Alignment.Center) {
            if (vm.error == null) CircularProgressIndicator(color = Fc.Lime)
            else Column { Text(vm.error.orEmpty(), color = Fc.Danger); TextButton(onClick = onClose) { Text("Вернуться") } }
        }
        return
    }
    if (plan == null) {
        Box(Modifier.fillMaxSize().background(Fc.Bg).padding(24.dp), contentAlignment = Alignment.Center) {
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
        text = { Column {
            Text("Сохраним ${vm.completedSets(plan, vm.draftUnitsKg).size} выполненных подходов и время тренировки. Выполнять весь план не обязательно. Неотмеченные подходы не считаются выполненными.")
            TextButton(onClick = { vm.saveForLater(onClose) }, enabled = !vm.finishing) { Text("Сохранить черновик и выйти") }
        } },
        confirmButton = { TextButton(onClick = { vm.finish(plan, vm.draftUnitsKg, onFinished) }, enabled = !vm.finishing) { Text(if (vm.finishing) "Сохраняю…" else "Завершить и сохранить") } },
        dismissButton = { TextButton(onClick = { confirmFinish = false }, enabled = !vm.finishing) { Text("Продолжить тренировку") } }
    )
    Box(Modifier.fillMaxSize().background(Fc.Bg)) {
        if (vm.inOverview || plan.items.isEmpty()) {
            WorkoutOverview(vm, plan, source, onOpen = vm::moveTo, onFinish = { confirmFinish = true }, onRest = { restOpen = true })
        } else {
            ExerciseMode(vm, plan, source, history, onTechnique, onFinish = { focus.clearFocus(); confirmFinish = true }, onRest = { restOpen = true })
        }
        if (restOpen && vm.rest > 0) RestOverlay(vm, plan, source, onClose = { restOpen = false })
    }
}

/** The phone stays awake for the whole workout, including long rests. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun RestEndSignal(count: Int) {
    val context = LocalContext.current
    LaunchedEffect(count) { if (count > 0) vibrate(context) }
}

private fun vibrate(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1))
    }
}

@Composable
private fun TopBar(elapsed: Long, onPlan: () -> Unit, onFinish: () -> Unit, finishing: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onPlan).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LineIcon(LineIcons.BACK, Fc.Text)
            Text("План", color = Fc.Text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { NumberText(time(elapsed), 22.sp) }
        OutlinedButton(onClick = onFinish, enabled = !finishing, border = BorderStroke(1.dp, Fc.Line),
            modifier = Modifier.heightIn(min = 44.dp).testTag("finish-workout"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Fc.Text)) { Text("Завершить", style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun Segments(count: Int, completed: Map<Int, Boolean>, current: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Fc.Line2)) {
                val fill = if (completed[i] == true) 1f else if (i == current) .5f else 0f
                if (fill > 0f) Box(Modifier.fillMaxWidth(fill).height(4.dp).background(Fc.Lime))
            }
        }
    }
}

/** A planned technique applies to all sets or only the last planned one. */
private fun plannedTechnique(item: ItemDraft, rowIndex: Int): String = when (item.techniqueScope) {
    "all" -> item.technique
    "last" -> if (rowIndex == item.sets - 1) item.technique else ""
    else -> ""
}

private fun weightStep(unitsKg: Boolean) = if (unitsKg) 2.5f else 5f

/** Weight and reps for a not-yet-done row under a pre-fill mode. */
private fun prefillValues(mode: Prefill, rowIndex: Int, last: List<SetLogEntity>, item: ItemDraft, unitsKg: Boolean): Pair<String, String> {
    val ref = last.getOrNull(rowIndex) ?: last.lastOrNull()
    if (mode == Prefill.EMPTY || ref == null) return suggestedWeight(item.exerciseId) to item.reps.defaultCount().toString()
    val progress = mode == Prefill.PROGRESS
    val shown = displayWeight(ref.weightKg, unitsKg) + if (progress && !ref.isTimed) weightStep(unitsKg) else 0f
    return number(shown) to (ref.reps + if (progress && ref.isTimed) 5 else 0).toString()
}

private fun applyRows(rows: MutableList<SetEntry>, values: (Int) -> Pair<String, String>) {
    rows.indices.forEach { i -> if (!rows[i].done) { val (w, r) = values(i); rows[i] = rows[i].copy(weight = w, reps = r) } }
}

private fun shortDate(epochDay: Long) = java.time.LocalDate.ofEpochDay(epochDay).format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale("ru")))

@Composable
private fun ExerciseMode(vm: WorkoutViewModel, plan: ScheduledDraft, source: WorkoutSource, history: List<WorkoutLog>,
    onTechnique: (String) -> Unit, onFinish: () -> Unit, onRest: () -> Unit) {
    val focus = LocalFocusManager.current
    val index = vm.exerciseIndex.coerceIn(0, plan.items.lastIndex)
    val item = plan.items[index]
    val exercise = source.catalog[item.exerciseId]
    val unitsKg = vm.draftUnitsKg
    val timed = item.reps == RepRange.TIME
    val earlier = remember(history, vm.startedAt) { history.filter { it.session.startedAt < vm.startedAt } }
    val last = remember(earlier, item.exerciseId, timed) { lastSessionSets(earlier, item.exerciseId, timed) }
    val lastSets = last?.second.orEmpty()
    val rows = vm.logs.getOrPut(index) {
        val mode = if (lastSets.isEmpty()) Prefill.EMPTY else Prefill.REPEAT
        vm.prefill[index] = mode
        mutableStateListOf<SetEntry>().apply { repeat(item.sets) { rowIndex ->
            val (w, r) = prefillValues(mode, rowIndex, lastSets, item, unitsKg)
            add(SetEntry(w, r, technique = plannedTechnique(item, rowIndex)))
        } }
    }
    LaunchedEffect(plan.id, index) { vm.startExercise() }
    val units = if (unitsKg) "кг" else "фунт."
    val doneRows = rows.filter { it.done }
    val heaviest = doneRows.filter { !it.warmup && !it.assisted }.maxWithOrNull(compareBy<SetEntry> { if (timed) (it.reps.toIntOrNull() ?: 0).toFloat() else weightKg(it.weight, unitsKg) }.thenBy { it.reps.toIntOrNull() ?: 0 })
    val previousBest = bestWorkingSet(earlier.flatMap { it.sets }, item.exerciseId, timed)
    val volume = if (timed) 0f else doneRows.filter { !it.assisted }.sumOf { row ->
        (weightKg(row.weight, unitsKg) * (row.reps.toIntOrNull() ?: 0) + row.stages.sumOf { (w, r) -> (weightKg(w, unitsKg) * (r.toIntOrNull() ?: 0)).toDouble() }).toDouble()
    }.toFloat()
    val summary = vm.summaryIndex != null
    val current = rows.indexOfFirst { !it.done }
    fun prevText(i: Int): String? = lastSets.getOrNull(i)?.let { if (timed) "${it.reps} с" else "${number(displayWeight(it.weightKg, unitsKg))} × ${it.reps}" }
    val labels = supersetLabels(plan.items)
    val group = supersetGroups(plan.items).firstOrNull { index in it }
    var weightHelp by remember(index) { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var techniqueFor by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(index, summary) { listState.scrollToItem(0) }

    fun hasRemaining(i: Int): Boolean = vm.logs[i]?.any { !it.done } ?: (plan.items[i].sets > 0)
    fun afterSetDone() {
        focus.clearFocus()
        if (group == null) { vm.startRest(item.restSec); return }
        // Superset: go straight to the next exercise of the round; rest only after the round.
        val position = group.indexOf(index)
        val nextInRound = group.drop(position + 1).firstOrNull(::hasRemaining)
        if (nextInRound != null) { vm.stopRest(); vm.moveTo(nextInRound); return }
        vm.startRest(item.restSec)
        group.firstOrNull(::hasRemaining)?.let { if (it != index) vm.moveTo(it) }
    }

    if (historyOpen) ModalBottomSheet(onDismissRequest = { historyOpen = false }, containerColor = Fc.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        HistorySheet(exercise?.name ?: item.exerciseId, earlier, item.exerciseId, timed, unitsKg) { sets ->
            vm.prefill.remove(index)
            applyRows(rows) { i -> val ref = sets.getOrNull(i) ?: sets.last(); number(displayWeight(ref.weightKg, unitsKg)) to ref.reps.toString() }
            historyOpen = false
        }
    }
    techniqueFor?.let { target ->
        ModalBottomSheet(onDismissRequest = { techniqueFor = null }, containerColor = Fc.Surface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            TechniqueSheet(exercise?.name ?: item.exerciseId, rows.getOrNull(target) ?: SetEntry(), target, rows.size) { warmup, technique, scope, rir, assisted ->
                val targets = when (scope) {
                    TechniqueScope.THIS -> listOf(target)
                    TechniqueScope.LAST -> listOf(rows.lastIndex)
                    TechniqueScope.ALL -> rows.indices.toList()
                }
                targets.forEach { i ->
                    val row = rows.getOrNull(i) ?: return@forEach
                    if (row.done && i != target) return@forEach
                    rows[i] = row.copy(warmup = warmup, technique = if (warmup) "" else technique.id,
                        stages = if (!warmup && technique.hasStages) row.stages else emptyList(),
                        rir = if (i == target) rir else row.rir, assisted = if (i == target) assisted else row.assisted)
                }
                techniqueFor = null
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(vm.elapsed, onPlan = { focus.clearFocus(); vm.overview() }, onFinish = onFinish, finishing = vm.finishing)
        Segments(plan.items.size, vm.completed, index)
        LazyColumn(Modifier.weight(1f).testTag("workout-list"), state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (exercise != null && FreeExerciseCatalog.media[exercise.id] != null) ExerciseThumbnail(exercise, Modifier.size(64.dp))
                    Column(Modifier.weight(1f)) {
                        Eyebrow("Упражнение ${index + 1} из ${plan.items.size}")
                        Text(exercise?.name ?: item.exerciseId, color = Fc.Text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 2.dp))
                        Text("${item.sets} × ${item.reps.label} · отдых ${restLabel(item.restSec)}", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (group != null) item { SupersetStrip(group, labels, plan, source, index, vm) }
            if (summary) {
                item {
                    FcCard(border = Fc.Lime) {
                        Text(if (doneRows.count { !it.warmup } < item.sets) "Упражнение выполнено частично" else "Упражнение завершено", color = Fc.Lime, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Подходов: ${doneRows.size}", color = Fc.Text)
                        if (timed) Text("Выполнено: ${doneRows.sumOf { it.reps.toIntOrNull() ?: 0 }} сек.", color = Fc.Text)
                        else Text("Тоннаж: ${number(displayWeight(volume, unitsKg))} $units", color = Fc.Text)
                        Text("Время упражнения: ${time(vm.duration(index) / 1000)}", color = Fc.Text)
                        Text((if (timed) "Самое долгое удержание: " else "Самый тяжёлый подход: ") + (heaviest?.let { if (timed) "${it.reps} сек." else "${number(displayWeight(weightKg(it.weight, unitsKg), unitsKg))} $units × ${it.reps}" } ?: "нет выполненных подходов"), color = Fc.Text)
                        Text("Рекорд за всю историю до этой тренировки: " + (previousBest?.let { if (it.isTimed) "${it.reps} сек." else "${number(displayWeight(it.weightKg, unitsKg))} $units × ${it.reps}" } ?: "первый результат"), color = Fc.Muted)
                        if (heaviest != null && previousBest != null) {
                            if (timed) {
                                val delta = (heaviest.reps.toIntOrNull() ?: 0) - previousBest.reps
                                Text("Сравнение по времени: ${if (delta > 0) "+" else ""}$delta сек.", color = if (delta > 0) Fc.Lime else Fc.Text)
                            } else {
                                val currentKg = weightKg(heaviest.weight, unitsKg)
                                val delta = displayWeight(currentKg - previousBest.weightKg, unitsKg)
                                Text("Сравнение по весу: ${if (delta > 0) "+" else ""}${number(delta)} $units", color = if (delta > 0) Fc.Lime else Fc.Text)
                                if (currentKg == previousBest.weightKg) Text("При том же весе: ${(heaviest.reps.toIntOrNull() ?: 0) - previousBest.reps} повт.", color = Fc.Muted)
                            }
                        }
                        TextButton(onClick = { vm.editExercise() }) { Text("Изменить подходы", color = Fc.Lime) }
                    }
                }
            } else {
                item {
                    PreviousCard(last, previousBest, timed, unitsKg, vm.prefill[index], onOpen = { historyOpen = true }) { mode ->
                        vm.prefill[index] = mode
                        applyRows(rows) { i -> prefillValues(mode, i, lastSets, item, unitsKg) }
                    }
                }
                if (item.comment.isNotBlank()) item { FcCard { Text("Комментарий к упражнению", color = Fc.Lime, style = MaterialTheme.typography.labelLarge); Text(item.comment, color = Fc.Text) } }
                rows.forEachIndexed { rowIndex, row ->
                    item(key = "set-$index-$rowIndex") {
                        val canRemove = rowIndex >= item.sets
                        val markDone: (Boolean) -> Unit = { checked ->
                            if (!checked || validSet(row)) {
                                rows[rowIndex] = row.copy(done = checked); vm.error = null
                                if (checked) afterSetDone()
                            } else vm.error = "Укажите вес не меньше 0 и количество больше 0"
                        }
                        if (rowIndex == current) CurrentSet(rowIndex, row, prevText(rowIndex), timed, unitsKg, canRemove,
                            label = labels[index], onChange = { rows[rowIndex] = it.copy(done = false) }, onDone = { markDone(true) },
                            onRemove = { rows.removeAt(rowIndex) }, onTechnique = { techniqueFor = rowIndex })
                        else CompactSet(rowIndex, row, prevText(rowIndex), upcoming = !row.done, canRemove = canRemove, units = units,
                            onToggle = { markDone(!row.done) }, onRemove = { rows.removeAt(rowIndex) })
                    }
                }
                item {
                    TextButton(onClick = { rows.add(SetEntry(weight = rows.lastOrNull()?.weight ?: suggestedWeight(item.exerciseId), reps = rows.lastOrNull()?.reps ?: item.reps.defaultCount().toString())) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("+ Добавить подход", color = Fc.Text, style = MaterialTheme.typography.labelLarge) }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = { onTechnique(item.exerciseId) }, contentPadding = PaddingValues(0.dp)) { Text("Техника выполнения →", color = Fc.Text, style = MaterialTheme.typography.bodySmall) }
                        TextButton(onClick = { weightHelp = !weightHelp }, contentPadding = PaddingValues(0.dp)) {
                            Text(if (weightHelp) "Скрыть подсказку" else "Как указывать вес?", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (weightHelp) Text("Вес — общий внешний вес снаряда; для пары гантелей укажите сумму. Собственный вес не включается; помощь тренажёра не считается тоннажем. В дропсете рекорд считается по первой ступени, тоннаж — по всем.",
                        color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                plan.items.getOrNull(index + 1)?.takeIf { group == null || index + 1 !in group }?.let { next ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Fc.Surface).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow("Следующее упражнение")
                            Text(source.catalog[next.exerciseId]?.name ?: next.exerciseId, color = Fc.Text, style = MaterialTheme.typography.titleSmall)
                        }
                        Text("${next.sets} × ${next.reps.label}", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (vm.error != null) Text(vm.error.orEmpty(), color = Fc.Danger, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Column(Modifier.fillMaxWidth().background(Fc.Bg).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.rest > 0) RestBar(vm, onRest)
            if (!summary) {
                val primary = current == -1
                Button(onClick = { focus.clearFocus(); vm.completeExercise() }, enabled = !vm.finishing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = if (primary) 60.dp else 48.dp).testTag("complete-exercise"), shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (primary) Fc.Lime else Fc.Surface2, contentColor = if (primary) Fc.OnAccent else Fc.Text)) {
                    Text("Завершить упражнение", style = MaterialTheme.typography.labelLarge)
                }
            }
            val lastItem = index == plan.items.lastIndex
            Button(onClick = { focus.clearFocus(); if (!lastItem) vm.moveTo(index + 1) else onFinish() },
                modifier = Modifier.fillMaxWidth().heightIn(min = if (summary) 60.dp else 44.dp).testTag("next-exercise"), enabled = !vm.finishing,
                shape = RoundedCornerShape(18.dp),
                colors = if (summary) ButtonDefaults.buttonColors(containerColor = Fc.Lime, contentColor = Fc.OnAccent)
                else ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Fc.Muted)) {
                Text(if (lastItem) "Итог тренировки" else if (summary) "Следующее →" else "Пропустить →", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Superset members with the current one highlighted. */
@Composable
private fun SupersetStrip(group: List<Int>, labels: Map<Int, String>, plan: ScheduledDraft, source: WorkoutSource, index: Int, vm: WorkoutViewModel) {
    val first = plan.items[group.first()]
    val round = (group.minOf { i -> vm.logs[i]?.count { it.done && !it.warmup } ?: 0 } + 1).coerceAtMost(first.sets)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Fc.Surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Суперсет ${labels[group.first()]?.first() ?: 'A'}", color = Fc.Text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            NumberText("Круг $round из ${first.sets}", 16.sp, color = Fc.Muted)
        }
        group.forEach { i ->
            val on = i == index
            val done = vm.logs[i]?.count { it.done && !it.warmup } ?: 0
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if (on) Modifier.background(Fc.LimeSoft) else Modifier)
                .clickable(role = Role.Button) { vm.moveTo(i) }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberText(labels[i] ?: "", 15.sp, color = if (on) Fc.Lime else Fc.Muted)
                Text(source.catalog[plan.items[i].exerciseId]?.name ?: plan.items[i].exerciseId, color = Fc.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("$done/${plan.items[i].sets}", color = Fc.Muted, style = MaterialTheme.typography.labelMedium)
            }
        }
        Text("Между упражнениями — без отдыха. Отдых ${restLabel(plan.items[group.last()].restSec)} — после круга.", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

/** Last session and all-time record, with the choice of how to pre-fill the sets. */
@Composable
private fun PreviousCard(last: Pair<WorkoutLog, List<SetLogEntity>>?, record: SetLogEntity?, timed: Boolean, unitsKg: Boolean,
    mode: Prefill?, onOpen: () -> Unit, onMode: (Prefill) -> Unit) {
    fun fmt(s: SetLogEntity) = if (timed) "${s.reps} с" else "${number(displayWeight(s.weightKg, unitsKg))}×${s.reps}"
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Fc.Surface).border(1.dp, Fc.Line2, RoundedCornerShape(22.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (last == null) {
            Text("Первая тренировка с этим упражнением", color = Fc.Text, style = MaterialTheme.typography.titleSmall)
            Text("Введите рабочий вес — в следующий раз он подставится сам.", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).semantics { contentDescription = "Прошлый результат и рекорд. Открыть историю" }
            .clickable(role = Role.Button, onClick = onOpen).testTag("previous-card"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Eyebrow("Прошлый раз · ${shortDate(last.first.session.epochDay)}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    last.second.forEach { s ->
                        Text(fmt(s), style = NumberStyle.copy(fontSize = 15.sp), color = Fc.Text,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Fc.Surface2).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            record?.let {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.clip(RoundedCornerShape(999.dp)).background(Fc.LimeSoft).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LineIcon(LineIcons.TROPHY, Fc.Lime, Modifier.size(14.dp), 2.4f)
                        Text(fmt(it).replace("×", " × "), color = Fc.Lime, style = MaterialTheme.typography.labelMedium)
                    }
                    Text("рекорд", color = Fc.Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
            LineIcon(LineIcons.NEXT, Fc.Dim, Modifier.size(18.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Triple(Prefill.EMPTY, "Пусто", "ввести вручную"), Triple(Prefill.REPEAT, "Повторить", "как ${shortDate(last.first.session.epochDay)}"),
                Triple(Prefill.PROGRESS, if (timed) "+5 с" else "+${number(weightStep(unitsKg))} ${if (unitsKg) "кг" else "фунт."}", "прогрессия")).forEach { (m, title, hint) ->
                val on = mode == m
                val shape = RoundedCornerShape(14.dp)
                Column(Modifier.weight(1f).heightIn(min = 52.dp).clip(shape)
                    .then(if (on) Modifier.background(Fc.Lime) else Modifier.border(1.dp, Fc.Line, shape))
                    .semantics { selected = on }.clickable(role = Role.RadioButton) { onMode(m) }.testTag("prefill-${m.name.lowercase()}").padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(title, color = if (on) Fc.OnAccent else Fc.Text, style = MaterialTheme.typography.labelLarge)
                    Text(hint, color = if (on) Fc.OnAccent.copy(alpha = .75f) else Fc.Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun HistorySheet(name: String, earlier: List<WorkoutLog>, exerciseId: String, timed: Boolean, unitsKg: Boolean, onFill: (List<SetLogEntity>) -> Unit) {
    val units = if (unitsKg) "кг" else "фунт."
    fun fmt(s: SetLogEntity) = if (timed) "${s.reps} с" else "${number(displayWeight(s.weightKg, unitsKg))}×${s.reps}"
    val sessions = earlier.mapNotNull { log ->
        val sets = log.sets.filter { it.exerciseId == exerciseId && it.isTimed == timed && it.done && !it.warmup && !it.assisted }
        if (sets.isEmpty()) null else log to sets
    }.sortedByDescending { it.first.session.startedAt }
    val all = sessions.flatMap { it.second }
    val record = bestWorkingSet(all, exerciseId, timed)
    val bestSet = all.maxByOrNull { it.weightKg * it.reps }
    val oneRm = all.filter { !timed }.maxOfOrNull { estimatedOneRepMax(it.weightKg, it.reps) }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow("История")
        Text(name, color = Fc.Text, style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("Рекорд", record?.let(::fmt), record?.let { shortDate(sessions.first { s -> it in s.second }.first.session.epochDay) }),
                Triple("Лучший подход", bestSet?.let(::fmt), "вес × повторы"),
                Triple(if (timed) "Тренировок" else "Оценка 1ПМ", if (timed) "${sessions.size}" else oneRm?.let { "${number(displayWeight(it, unitsKg))} $units" }, if (timed) "с упражнением" else "по Эпли")
            ).forEachIndexed { i, (label, value, sub) ->
                val accent = i == 0
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (accent) Fc.Lime else Fc.Surface2).padding(10.dp)) {
                    Text(label, color = if (accent) Fc.OnAccent else Fc.Muted, style = MaterialTheme.typography.labelSmall)
                    NumberText(value ?: "—", 20.sp, color = if (accent) Fc.OnAccent else Fc.Text)
                    Text(sub.orEmpty(), color = if (accent) Fc.OnAccent.copy(alpha = .75f) else Fc.Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Eyebrow("Последние тренировки")
        if (sessions.isEmpty()) Text("Истории пока нет.", color = Fc.Muted)
        sessions.take(3).forEach { (log, sets) ->
            val best = bestWorkingSet(sets, exerciseId, timed)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, Fc.Line2, RoundedCornerShape(18.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row {
                    Text(java.time.LocalDate.ofEpochDay(log.session.epochDay).format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMMM", Locale("ru"))).replaceFirstChar { it.uppercase() },
                        color = Fc.Text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (!timed) Text("${number(displayWeight(sets.sumOf { setVolumeKg(it).toDouble() }.toFloat(), unitsKg))} $units", color = Fc.Muted, style = MaterialTheme.typography.labelMedium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sets.forEach { s ->
                        val top = s == best
                        Text(fmt(s), style = NumberStyle.copy(fontSize = 15.sp), color = if (top) Fc.Lime else Fc.Text,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (top) Fc.LimeSoft else Fc.Surface2).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                ActionButton("Заполнить подходы как ${shortDate(log.session.epochDay)}", { onFill(sets) }, outlined = true)
            }
        }
    }
}

@Composable
private fun TechniqueSheet(name: String, row: SetEntry, rowIndex: Int, rowCount: Int,
    onApply: (warmup: Boolean, technique: Technique, scope: TechniqueScope, rir: String, assisted: Boolean) -> Unit) {
    var warmup by remember { mutableStateOf(row.warmup) }
    var technique by remember { mutableStateOf(Technique.of(row.technique)) }
    var scope by remember { mutableStateOf(TechniqueScope.THIS) }
    var rir by remember { mutableStateOf(row.rir) }
    var assisted by remember { mutableStateOf(row.assisted) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow(name)
        Text("Как выполнить подход ${rowIndex + 1}", color = Fc.Text, style = MaterialTheme.typography.headlineMedium)
        Segmented(TechniqueScope.entries, scope, { scope = it }, { if (it == TechniqueScope.LAST) "Последний ($rowCount)" else it.title })
        val options = listOf<Pair<Technique?, Boolean>>(Technique.NORMAL to false, null to true) + Technique.entries.drop(1).map { it to false }
        options.forEach { (t, isWarmup) ->
            val on = if (isWarmup) warmup else !warmup && technique == t
            val shape = RoundedCornerShape(16.dp)
            Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(shape)
                .then(if (on) Modifier.background(Fc.LimeSoft).border(1.5.dp, Fc.Lime, shape) else Modifier.border(1.dp, Fc.Line2, shape))
                .semantics { selected = on }.clickable(role = Role.RadioButton) { if (isWarmup) { warmup = true } else { warmup = false; technique = t!! } }
                .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp).testTag("technique-${if (isWarmup) "warmup" else t!!.id.ifBlank { "normal" }}"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Fc.Lime else Fc.Surface2), contentAlignment = Alignment.Center) {
                    NumberText(if (isWarmup) "Р" else t!!.short, 15.sp, color = if (on) Fc.OnAccent else Fc.Muted)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (isWarmup) "Разминка" else t!!.title, color = Fc.Text, style = MaterialTheme.typography.titleSmall)
                    Text(if (isWarmup) "Не входит в рекорды и тоннаж" else t!!.hint, color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Eyebrow("Дополнительно · подход ${rowIndex + 1}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Повторов в запасе", color = Fc.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            var menu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { menu = true }, border = BorderStroke(1.dp, Fc.Line), modifier = Modifier.heightIn(min = 44.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Fc.Text)) { Text(rir.ifBlank { "не указано" }) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Не указывать") }, onClick = { rir = ""; menu = false })
                    (0..5).forEach { n -> DropdownMenuItem(text = { Text("$n") }, onClick = { rir = "$n"; menu = false }) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Switch) { assisted = !assisted }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Вес помощи тренажёра", color = Fc.Text, style = MaterialTheme.typography.bodyMedium)
                Text("Не считается в тоннаже", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = assisted, onCheckedChange = { assisted = it },
                colors = SwitchDefaults.colors(checkedTrackColor = Fc.Lime, checkedThumbColor = Fc.OnAccent))
        }
        PrimaryButton("Применить", { onApply(warmup, technique, scope, rir, assisted) }, Modifier.testTag("technique-apply"))
    }
}

/** The set being performed: large steppers instead of a keyboard. */
@Composable
private fun CurrentSet(rowIndex: Int, row: SetEntry, previous: String?, timed: Boolean, unitsKg: Boolean, canRemove: Boolean, label: String?,
    onChange: (SetEntry) -> Unit, onDone: () -> Unit, onRemove: () -> Unit, onTechnique: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val technique = Technique.of(row.technique)
    val step = weightStep(unitsKg)
    Column(Modifier.fillMaxWidth().clip(shape).background(Fc.Surface).border(1.5.dp, Fc.Lime, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text((label?.let { "$it · " } ?: "") + if (row.warmup) "Разминка" else "Подход ${rowIndex + 1}", style = MaterialTheme.typography.headlineSmall, color = Fc.Text)
                previous?.let { Text("в прошлый раз $it", color = Fc.Muted, style = MaterialTheme.typography.bodySmall) }
            }
            val on = row.warmup || technique != Technique.NORMAL
            Row(Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp))
                .then(if (on) Modifier.background(Fc.LimeSoft) else Modifier.border(1.dp, Fc.Line, RoundedCornerShape(20.dp)))
                .semantics { contentDescription = "Как выполнить подход: ${if (row.warmup) "разминка" else technique.title}" }
                .clickable(role = Role.Button, onClick = onTechnique).testTag("technique-$rowIndex").padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (row.warmup) "Разминка" else technique.title, color = if (on) Fc.Lime else Fc.Text, style = MaterialTheme.typography.labelLarge)
                LineIcon("M6 9l6 6 6-6", if (on) Fc.Lime else Fc.Text, Modifier.size(14.dp), 2.4f)
            }
            if (canRemove) IconButton(onClick = onRemove, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Delete, "Удалить подход ${rowIndex + 1}", tint = Fc.Muted) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stepper(if (unitsKg) "Вес, кг" else "Вес, фунт.", row.weight, step, decimals = true,
                tag = "weight-$rowIndex", description = "Вес подхода ${rowIndex + 1}", modifier = Modifier.weight(1f)) { onChange(row.copy(weight = it)) }
            Stepper(if (timed) "Секунды" else "Повторы", row.reps, if (timed) 5f else 1f, decimals = false,
                tag = "reps-$rowIndex", description = "Количество подхода ${rowIndex + 1}", modifier = Modifier.weight(1f)) { onChange(row.copy(reps = it)) }
        }
        if (!row.warmup && technique.hasStages) {
            row.stages.forEachIndexed { s, (w, r) ->
                Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stepper(if (technique == Technique.DROP) "Ступень ${s + 2}" else "Мини-подход ${s + 2}", w, step, decimals = true,
                        tag = "stage-weight-$rowIndex-$s", description = "Вес ступени ${s + 2}", modifier = Modifier.weight(1f), compact = true) { value ->
                        onChange(row.copy(stages = row.stages.mapIndexed { k, p -> if (k == s) value to p.second else p }))
                    }
                    Stepper("Повторы", r, 1f, decimals = false, tag = "stage-reps-$rowIndex-$s", description = "Повторы ступени ${s + 2}",
                        modifier = Modifier.weight(1f), compact = true) { value ->
                        onChange(row.copy(stages = row.stages.mapIndexed { k, p -> if (k == s) p.first to value else p }))
                    }
                    IconButton(onClick = { onChange(row.copy(stages = row.stages.filterIndexed { k, _ -> k != s })) }, modifier = Modifier.size(44.dp)) {
                        LineIcon(LineIcons.CLOSE, Fc.Muted, Modifier.size(18.dp))
                    }
                }
            }
            val base = row.stages.lastOrNull() ?: (row.weight to row.reps)
            val baseWeight = base.first.replace(',', '.').toFloatOrNull() ?: 0f
            val nextWeight = if (technique == Technique.DROP) (kotlin.math.round(baseWeight * 0.8f / step) * step).coerceAtLeast(0f) else baseWeight
            val nextReps = if (technique == Technique.DROP) (base.second.toIntOrNull() ?: 8).coerceAtLeast(1) else ((base.second.toIntOrNull() ?: 6) / 3).coerceAtLeast(1)
            DashedButton("+ ${if (technique == Technique.DROP) "Ступень" else "Мини-подход"} · ${number(nextWeight)} × $nextReps", {
                onChange(row.copy(stages = row.stages + (number(nextWeight) to nextReps.toString())))
            }, Modifier.testTag("add-stage-$rowIndex"))
            Text(if (technique == Technique.DROP) "Без отдыха между ступенями. Отдых начнётся после последней." else "Пауза 10–20 с между мини-подходами. Отдых начнётся после последнего.",
                color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onDone, shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Fc.Lime, contentColor = Fc.OnAccent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("done-$rowIndex").semantics {
                toggleableState = ToggleableState.Off
                stateDescription = "Не выполнен"
            }) {
            Icon(Icons.Filled.Check, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (!row.warmup && technique.hasStages && row.stages.isNotEmpty()) "${technique.title} выполнен" else "Подход выполнен", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, step: Float, decimals: Boolean, tag: String, description: String,
    modifier: Modifier, compact: Boolean = false, onValue: (String) -> Unit) {
    val height = if (compact) 48.dp else 64.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Fc.Muted, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(if (compact) 14.dp else 18.dp)).background(Fc.Surface2), verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", "Меньше: ${description.lowercase()}", compact) { onValue(stepValue(value, -step, decimals)) }
            BasicTextField(value, { text ->
                onValue(if (decimals) com.fitclub.tracker.domain.normalizeDecimalInput(text, 6) else text.filter(Char::isDigit).take(4))
            }, modifier = Modifier.weight(1f).testTag(tag).semantics { contentDescription = description },
                singleLine = true, textStyle = NumberStyle.copy(fontSize = if (compact) 22.sp else 30.sp, color = Fc.Text, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number),
                cursorBrush = SolidColor(Fc.Lime),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (value.isEmpty()) Text("—", style = NumberStyle.copy(fontSize = if (compact) 22.sp else 30.sp), color = Fc.Dim, textAlign = TextAlign.Center)
                        inner()
                    }
                })
            StepButton("+", "Больше: ${description.lowercase()}", compact) { onValue(stepValue(value, step, decimals)) }
        }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, compact: Boolean, onClick: () -> Unit) {
    Box(Modifier.width(if (compact) 40.dp else 48.dp).fillMaxHeight().semantics { contentDescription = description }.clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(symbol, color = Fc.Text, fontSize = if (compact) 22.sp else 26.sp)
    }
}

internal fun stepValue(value: String, step: Float, decimals: Boolean): String {
    val current = value.replace(',', '.').toFloatOrNull() ?: 0f
    val next = (current + step).coerceAtLeast(0f)
    return if (decimals) number(next) else next.toInt().toString()
}

/** A finished or upcoming set: one compact line with its check. */
@Composable
private fun CompactSet(rowIndex: Int, row: SetEntry, previous: String?, upcoming: Boolean, canRemove: Boolean, units: String,
    onToggle: () -> Unit, onRemove: () -> Unit) {
    val technique = Technique.of(row.technique)
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberText(if (row.warmup) "Р" else "${rowIndex + 1}", 16.sp, Modifier.width(24.dp), color = if (row.done && !row.warmup) Fc.Lime else Fc.Muted)
        Column(Modifier.weight(1f)) {
            val main = "${row.weight.ifBlank { "—" }} $units × ${row.reps.ifBlank { "—" }}"
            NumberText(main + row.stages.joinToString("") { (w, r) -> " → $w × $r" }, 19.sp, color = if (upcoming) Fc.Muted else Fc.Text)
            val notes = listOfNotNull(technique.takeIf { it != Technique.NORMAL && !row.warmup }?.title, previous?.let { "было $it" }).joinToString(" · ")
            if (notes.isNotEmpty()) Text(notes, color = Fc.Muted, style = MaterialTheme.typography.labelSmall)
        }
        if (canRemove && !row.done) IconButton(onClick = onRemove, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Delete, "Удалить подход ${rowIndex + 1}", tint = Fc.Muted) }
        Box(Modifier.size(44.dp).clip(CircleShape)
            .then(if (row.done) Modifier.background(if (row.warmup) Fc.Line else Fc.Lime) else Modifier.border(1.5.dp, Fc.Line, CircleShape))
            .semantics {
                toggleableState = if (row.done) ToggleableState.On else ToggleableState.Off
                stateDescription = if (row.done) "Выполнен" else "Не выполнен"
                contentDescription = "Подход ${rowIndex + 1} выполнен"
            }
            .clickable(role = Role.Checkbox, onClick = onToggle).testTag("done-$rowIndex"), contentAlignment = Alignment.Center) {
            if (row.done) Icon(Icons.Filled.Check, null, tint = if (row.warmup) Fc.Text else Fc.OnAccent, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun RestBar(vm: WorkoutViewModel, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Fc.Surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                .semantics { contentDescription = "Отдых ${time(vm.rest.toLong())}. Открыть таймер" }
                .clickable(role = Role.Button, onClick = onOpen), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineIcon(LineIcons.TIMER, Fc.Ember)
                Column {
                    Text("Отдых · из ${time(vm.restTotal.toLong())}", color = Fc.Muted, style = MaterialTheme.typography.labelMedium)
                    NumberText(time(vm.rest.toLong()), 26.sp, color = Fc.Ember)
                }
            }
            OutlinedButton(onClick = { vm.addRest(15) }, border = BorderStroke(1.dp, Fc.Line), modifier = Modifier.heightIn(min = 44.dp),
                contentPadding = PaddingValues(horizontal = 12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Fc.Text)) { Text("+15 с") }
            Button(onClick = vm::stopRest, modifier = Modifier.heightIn(min = 44.dp), contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Fc.Surface2, contentColor = Fc.Text)) { Text("Пропустить") }
        }
        LinearProgressIndicator(progress = { if (vm.restTotal > 0) vm.rest.toFloat() / vm.restTotal else 0f },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = Fc.Ember, trackColor = Fc.Line2)
    }
}

/** Full-screen rest: digits readable at arm's length. */
@Composable
private fun RestOverlay(vm: WorkoutViewModel, plan: ScheduledDraft, source: WorkoutSource, onClose: () -> Unit) {
    val index = vm.exerciseIndex.coerceIn(0, (plan.items.size - 1).coerceAtLeast(0))
    val item = plan.items.getOrNull(index)
    val rows = vm.logs[index].orEmpty()
    val next = rows.indexOfFirst { !it.done }
    val units = if (vm.draftUnitsKg) "кг" else "фунт."
    val ember = Fc.Ember
    val track = Fc.Line2
    Column(Modifier.fillMaxSize().background(Fc.Bg).pointerInput(Unit) { detectTapGestures { } }.padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 44.dp)) {
                LineIcon(LineIcons.DOWN, Fc.Text, Modifier.size(20.dp)); Spacer(Modifier.width(4.dp)); Text("Свернуть", color = Fc.Text)
            }
            Spacer(Modifier.weight(1f))
            NumberText(time(vm.elapsed), 18.sp, color = Fc.Muted)
        }
        item?.let {
            Text(source.catalog[it.exerciseId]?.name ?: it.exerciseId, color = Fc.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Text("выполнено ${rows.count { r -> r.done && !r.warmup }} из ${it.sets} подходов", color = Fc.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Box(Modifier.padding(top = 24.dp).size(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 16.dp.toPx()
                val inset = stroke / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                val fraction = if (vm.restTotal > 0) vm.rest.toFloat() / vm.restTotal else 0f
                drawArc(ember, -90f, 360f * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Eyebrow("Отдых", color = Fc.Ember)
                NumberText(time(vm.rest.toLong()), 88.sp)
                Text("из ${time(vm.restTotal.toLong())}", color = Fc.Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(-15, 15).forEach { delta ->
                OutlinedButton(onClick = { vm.addRest(delta) }, border = BorderStroke(1.dp, Fc.Line), shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Fc.Text)) {
                    NumberText(if (delta > 0) "+15 с" else "−15 с", 20.sp)
                }
            }
        }
        if (item != null && next >= 0) {
            val row = rows[next]
            Column(Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(24.dp)).background(Fc.Surface).padding(16.dp)) {
                Eyebrow(if (row.warmup) "Следующий · разминка" else "Следующий · подход ${next + 1}")
                NumberText(if (item.reps == RepRange.TIME) "${row.weight.ifBlank { "0" }} $units × ${row.reps} с" else "${row.weight.ifBlank { "0" }} $units × ${row.reps}", 32.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Готов к подходу", { vm.stopRest(); onClose() }, Modifier.padding(bottom = 8.dp))
    }
}

@Composable
internal fun SetRow(index: Int, row: SetEntry, canRemove: Boolean, onDone: (Boolean) -> Unit, onRemove: () -> Unit, allowCompletedRemoval: Boolean = false, onEdit: (String, String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${index + 1}", color = if (row.done) Fc.Lime else Fc.Text, textAlign = TextAlign.Center, modifier = Modifier.width(26.dp))
        OutlinedTextField(row.weight, { onEdit(com.fitclub.tracker.domain.normalizeDecimalInput(it), row.reps) }, singleLine = true,
            isError = row.weight.isNotBlank() && row.weight.replace(',', '.').toFloatOrNull()?.let { !it.isFinite() || it < 0 } != false,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).semantics { contentDescription = "Вес подхода ${index + 1}" }.testTag("weight-$index"), shape = RoundedCornerShape(14.dp), colors = fieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(row.reps, { onEdit(row.weight, it.filter(Char::isDigit)) }, singleLine = true,
            isError = row.reps.isBlank() || (row.reps.toIntOrNull() ?: 0) <= 0,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).semantics { contentDescription = "Количество подхода ${index + 1}" }.testTag("reps-$index"), shape = RoundedCornerShape(14.dp), colors = fieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        IconButton(onClick = { onDone(!row.done) }, modifier = Modifier.size(48.dp).semantics {
            toggleableState = if (row.done) ToggleableState.On else ToggleableState.Off
            stateDescription = if (row.done) "Выполнен" else "Не выполнен"
        }.testTag("done-$index")) {
            Icon(Icons.Filled.Check, "Подход ${index + 1} выполнен", tint = if (row.done) Fc.OnAccent else Fc.Muted,
                modifier = Modifier.background(if (row.done) Fc.Lime else Fc.Surface2, CircleShape).padding(5.dp))
        }
        // Reserve the same column for every row, including planned and completed sets.
        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
            if (canRemove && (!row.done || allowCompletedRemoval)) IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Delete, "Удалить подход ${index + 1}", tint = Fc.Muted) }
        }
    }
}

internal fun validSet(row: SetEntry): Boolean {
    val w = row.weight.replace(',', '.').toFloatOrNull()
    return w != null && w.isFinite() && w >= 0 && (row.reps.toIntOrNull() ?: 0) > 0 && (row.rir.isBlank() || row.rir.toIntOrNull() in 0..10)
}
private fun weightKg(text: String, unitsKg: Boolean) = (text.replace(',', '.').toFloatOrNull() ?: 0f) / if (unitsKg) 1f else 2.2046226f
private fun displayWeight(kg: Float, unitsKg: Boolean) = kg * if (unitsKg) 1f else 2.2046226f
private fun number(value: Float) = com.fitclub.tracker.domain.decimal(value)
@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedTextColor = Fc.Text, unfocusedTextColor = Fc.Text, focusedBorderColor = Fc.Lime, unfocusedBorderColor = Fc.Line, cursorColor = Fc.Lime)
private fun time(sec: Long) = "%02d:%02d".format(Locale.US, sec / 60, sec % 60)
private fun suggestedWeight(id: String) = if (id in listOf("plank", "pushup", "pullup", "hangraise")) "0" else ""



@Composable
private fun WorkoutOverview(vm: WorkoutViewModel, plan: ScheduledDraft, source: WorkoutSource,
    onOpen: (Int) -> Unit, onFinish: () -> Unit, onRest: () -> Unit) {
    var picker by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Int?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
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
        confirmButton = { TextButton(onClick = { removing?.let(vm::removeExercise); removing = null }) { Text("Убрать", color = Fc.Danger) } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text("Отмена") } })
    val resume = plan.items.indices.firstOrNull { vm.completed[it] != true }
    val labels = supersetLabels(plan.items)
    var supersetFor by remember { mutableStateOf<Int?>(null) }
    supersetFor?.let { target ->
        SupersetDialog(target, plan.items.map { source.catalog[it.exerciseId]?.name ?: it.exerciseId }, labels,
            onPick = { partner -> vm.setSuperset(target, partner); supersetFor = null }, onDismiss = { supersetFor = null })
    }
    Column(Modifier.fillMaxSize().background(Fc.Bg)) {
        LazyColumn(Modifier.weight(1f).testTag("workout-overview"), state = list,
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Eyebrow("Идёт тренировка", color = Fc.Lime)
                Text(plan.title, color = Fc.Text, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                val volume = vm.completedSets(plan, vm.draftUnitsKg).filter { !it.isTimed && !it.assisted }.sumOf { (it.weightKg * it.reps).toDouble() }
                val weightUnits = WeightUnits(vm.draftUnitsKg)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Fc.Surface).padding(vertical = 14.dp)) {
                    listOf(time(vm.elapsed) to "время", "${vm.completed.count { it.value }} / ${plan.items.size}" to "упражнений",
                        weightUnits.number(weightUnits.fromKg(volume.toFloat())) to "${weightUnits.label} объём").forEachIndexed { i, (value, label) ->
                        if (i > 0) Box(Modifier.width(1.dp).height(44.dp).background(Fc.Line2))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            NumberText(value, 24.sp)
                            Text(label, color = Fc.Muted, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Упражнения", Modifier.weight(1f))
                    TextButton(onClick = { editing = !editing }, modifier = Modifier.heightIn(min = 44.dp)) {
                        Text(if (editing) "Готово" else "Изменить", color = Fc.Text, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            itemsIndexed(plan.items, key = { i, _ -> vm.tokens.getOrNull(i) ?: i }) { index, item ->
                val token = vm.tokens.getOrNull(index) ?: index
                val done = vm.completed[index] == true
                val begun = vm.started[index] == true
                val exercise = source.catalog[item.exerciseId]
                val doneSets = vm.logs[index].orEmpty().count { it.done && !it.warmup }
                val shape = RoundedCornerShape(20.dp)
                Column(Modifier.fillMaxWidth().clip(shape)
                    .background(if (done) Color.Transparent else Fc.Surface)
                    .border(if (begun && !done || dragging == token) 1.5.dp else 1.dp, if (begun && !done || dragging == token) Fc.Lime else if (done) Fc.Line2 else Fc.Surface, shape)
                    .clickable(role = Role.Button) { onOpen(index) }.padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).then(when {
                            done -> Modifier.background(Fc.Lime)
                            begun -> Modifier.border(2.dp, Fc.Lime, CircleShape)
                            else -> Modifier.background(Fc.Surface2)
                        }), contentAlignment = Alignment.Center) {
                            if (done) Icon(Icons.Filled.Check, null, tint = Fc.OnAccent, modifier = Modifier.size(22.dp))
                            else NumberText(if (begun) "$doneSets/${item.sets}" else labels[index] ?: "${index + 1}", if (begun) 14.sp else 17.sp, color = if (begun || labels[index] != null) Fc.Lime else Fc.Muted)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(exercise?.name ?: item.exerciseId, color = Fc.Text, style = MaterialTheme.typography.titleSmall)
                            Text(if (done) "Завершено" else if (begun) "Начато · продолжить" else "${item.sets} × ${item.reps.label}",
                                color = if (begun && !done) Fc.Lime else Fc.Muted, style = MaterialTheme.typography.bodySmall)
                            val tags = listOfNotNull(labels[index]?.let { "Суперсет ${it.first()}" },
                                Technique.of(item.technique).takeIf { it != Technique.NORMAL && item.techniqueScope.isNotBlank() }
                                    ?.let { "${it.title} · ${if (item.techniqueScope == "all") "все подходы" else "последний"}" })
                            if (tags.isNotEmpty()) Pill(tags.joinToString(" · "), Fc.LimeSoft, Fc.Lime, Modifier.padding(top = 4.dp))
                        }
                        Box(Modifier.size(44.dp).semantics { contentDescription = "Перетащите, чтобы изменить порядок" }.testTag("drag-exercise-$token").pointerInput(token) {
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
                        }, contentAlignment = Alignment.Center) { Text("≡", color = Fc.Dim, style = MaterialTheme.typography.headlineSmall) }
                    }
                    if (editing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { if (index > 0) vm.reorder(index, index - 1) }, enabled = index > 0) { Text("↑", modifier = Modifier.semantics { contentDescription = "Переместить упражнение выше" }) }
                        TextButton(onClick = { if (index < plan.items.lastIndex) vm.reorder(index, index + 1) }, enabled = index < plan.items.lastIndex) { Text("↓", modifier = Modifier.semantics { contentDescription = "Переместить упражнение ниже" }) }
                        TextButton(onClick = { removing = index }) { Text("Убрать", color = Fc.Danger) }
                    }
                }
                if (editing && plan.items.size > 1) {
                    val label = labels[index]
                    Row(Modifier.padding(start = 20.dp, top = 6.dp).heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp))
                        .then(if (label != null) Modifier.background(Fc.LimeSoft) else Modifier.border(1.dp, Fc.Line, RoundedCornerShape(20.dp)))
                        .clickable(role = Role.Button) { supersetFor = index }.testTag("superset-$index").padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LineIcon("M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7", if (label != null) Fc.Lime else Fc.Muted, Modifier.size(16.dp))
                        Text(if (label != null) "Суперсет ${label.first()} · изменить" else "Суперсет с…", color = if (label != null) Fc.Lime else Fc.Muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item { DashedButton("+ Добавить упражнение", { picker = true }) }
            if (vm.error != null) item { Text(vm.error.orEmpty(), color = Fc.Danger) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (vm.rest > 0) RestBar(vm, onRest)
            if (resume != null) {
                PrimaryButton("Продолжить · " + (source.catalog[plan.items[resume].exerciseId]?.name ?: plan.items[resume].exerciseId), { onOpen(resume) })
                TextButton(onClick = onFinish, enabled = !vm.finishing, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("finish-workout")) {
                    Text("Завершить тренировку", color = Fc.Muted, style = MaterialTheme.typography.labelLarge)
                }
            } else PrimaryButton("Завершить тренировку", { if (!vm.finishing) onFinish() }, Modifier.testTag("finish-workout"))
        }
    }
}

/** Pick any other exercise to pair with; an exercise already in a superset brings its group. */
@Composable
internal fun SupersetDialog(target: Int, names: List<String>, labels: Map<Int, String>, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Суперсет: ${names.getOrNull(target).orEmpty()}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("С каким упражнением чередовать без отдыха? Порядок в тренировке не меняется.", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                names.forEachIndexed { i, name ->
                    if (i == target) return@forEachIndexed
                    val together = labels[i] != null && labels[i]?.first() == labels[target]?.first()
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                        .then(if (together) Modifier.background(Fc.LimeSoft) else Modifier)
                        .clickable(role = Role.Button) { onPick(i) }.testTag("superset-pick-$i").padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberText(labels[i] ?: "${i + 1}", 15.sp, color = if (labels[i] != null) Fc.Lime else Fc.Muted)
                        Text(name, color = Fc.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (together) LineIcon(LineIcons.CHECK, Fc.Lime, Modifier.size(18.dp))
                    }
                }
            }
        },
        confirmButton = { if (labels[target] != null) TextButton(onClick = { onPick(null) }) { Text("Убрать из суперсета", color = Fc.Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}
