package com.fitclub.tracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitclub.tracker.domain.LocalWeightUnits
import com.fitclub.tracker.data.db.ActiveWorkoutEntity
import com.fitclub.tracker.AppContainer
import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.repo.*
import com.fitclub.tracker.ui.components.*
import com.fitclub.tracker.ui.navigation.appViewModel
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CalendarState(
    val plans: List<ScheduledDraft> = emptyList(),
    val catalog: Map<String, ExerciseEntity> = emptyMap(),
    val workouts: List<WorkoutLog> = emptyList(),
    val active: List<ActiveWorkoutEntity> = emptyList()
)

class CalendarViewModel(private val c: AppContainer) : ViewModel() {
    val error = MutableStateFlow<String?>(null)
    fun deletePlan(id: Long) { viewModelScope.launch {
        runCatching { c.schedule.delete(id) }.onFailure { error.value = "Не удалось удалить: ${it.message}" }
    } }
    fun deleteWorkout(id: Long) { viewModelScope.launch {
        runCatching { c.fitness.deleteWorkout(id) }.onFailure { error.value = "Не удалось удалить: ${it.message}" }
    } }
    val state = combine(c.schedule.observeAll(), c.exercises.observeAll(), c.fitness.observeWorkouts(), c.fitness.observeActive()) { p, e, w, a ->
        CalendarState(p, e.associateBy { it.id }, w, a)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())
}

fun calendarDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay)
    .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))
fun scheduleTime(minute: Int) = "%02d:%02d".format(Locale.US, minute / 60, minute % 60)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarHomeScreen(onPlan: (Long) -> Unit, onStart: (Long) -> Unit, onProfile: () -> Unit,
    onEditPlan: (Long, Long) -> Unit = { date, _ -> onPlan(date) }, onResult: (Long) -> Unit = {}, initialEpoch: Long? = null,
    onBack: (() -> Unit)? = null) {
    val vm = appViewModel { CalendarViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val units = LocalWeightUnits.current
    val today = LocalDate.now()
    val start = initialEpoch?.let(LocalDate::ofEpochDay) ?: today
    var selectedEpoch by rememberSaveable { mutableLongStateOf(start.toEpochDay()) }
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.from(start).toString()) }
    val month = YearMonth.parse(monthValue)
    val selectedDate = LocalDate.ofEpochDay(selectedEpoch)
    val plans = state.plans.filter { it.epochDay == selectedEpoch }.sortedBy { it.startMinute }
    val error by vm.error.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Pair<Boolean, Long>?>(null) }
    if (deleting != null) AlertDialog(onDismissRequest = { deleting = null },
        title = { Text(if (deleting!!.first) "Удалить выполненную тренировку?" else "Удалить запланированную тренировку?") },
        text = { Text(if (deleting!!.first) "Результаты и связанный план этой тренировки будут удалены. Остальные тренировки сохранятся." else "Этот план будет удалён.") },
        confirmButton = { TextButton(onClick = {
            deleting?.let { (done, id) -> if (done) vm.deleteWorkout(id) else vm.deletePlan(id) }; deleting = null
        }) { Text("Удалить", color = Fc.Pink) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } })
    val logs = state.workouts.filter { it.session.epochDay == selectedEpoch }
    val completed = state.workouts.filter { it.completedSets > 0 }.map { it.session.epochDay }.toSet()
    val planned = state.plans.associateBy { it.epochDay }
    val doneThisMonth = state.workouts.filter { it.completedSets > 0 }.count { YearMonth.from(LocalDate.ofEpochDay(it.session.epochDay)) == month }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (onBack != null) item { BackRow("Сегодня", onBack) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("История и планы")
                    ScreenTitle("Календарь")
                }
            }
        }
        item {
            FcCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { monthValue = month.minusMonths(1).toString() }) { Text("‹", modifier = Modifier.semantics { contentDescription = "Предыдущий месяц" }, color = Fc.Text, style = MaterialTheme.typography.headlineSmall) }
                    Text(month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))).replaceFirstChar { it.uppercase() },
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Fc.Text, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { monthValue = month.plusMonths(1).toString() }) { Text("›", modifier = Modifier.semantics { contentDescription = "Следующий месяц" }, color = Fc.Text, style = MaterialTheme.typography.headlineSmall) }
                }
                // Seven equal columns that always fit the card: no minimum width, no sideways scrolling.
                Column(Modifier.fillMaxWidth().testTag("calendar-grid")) {
                        Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                        Text(it, color = Fc.Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                val offset = month.atDay(1).dayOfWeek.value - 1
                val cells = List(offset) { 0 } + (1..month.lengthOfMonth()).toList()
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        (week + List(7 - week.size) { 0 }).forEach { number ->
                            if (number == 0) Spacer(Modifier.weight(1f).aspectRatio(1f)) else {
                                val date = month.atDay(number)
                                val epoch = date.toEpochDay()
                                val selected = epoch == selectedEpoch
                                val done = epoch in completed
                                val hasPlan = epoch in planned
                                val color = when { selected -> Fc.Lime; done -> Fc.LimeSoft; hasPlan -> Fc.LimeSoft; else -> Color.Transparent }
                                Column(Modifier.weight(1f).heightIn(min = 44.dp).padding(1.dp)
                                    .background(color, RoundedCornerShape(10.dp))
                                    .then(if (date == today && !selected) Modifier.border(1.dp, Fc.Lime, RoundedCornerShape(10.dp)) else Modifier)
                                    .semantics { this.selected = selected; contentDescription = calendarDate(epoch) + when { done -> ", выполнено"; hasPlan -> ", тренировка запланирована"; else -> ", запланировать тренировку" } }
                                    .clickable(role = Role.Button) { selectedEpoch = epoch },
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text("$number", color = if (selected) Fc.OnAccent else if (hasPlan || done) Fc.Lime else Fc.Text,
                                        style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal, maxLines = 1, softWrap = false)
                                    if (done || hasPlan) Box(Modifier.padding(top = 3.dp).size(4.dp).background(if (selected) Fc.OnAccent else Fc.Lime, CircleShape))
                                }
                            }
                        }
                    }
                }
                        }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    // Legend: a ring marks a plan, a filled dot a finished workout.
                    Text("○ План", color = Fc.Lime, style = MaterialTheme.typography.labelSmall)
                    Text("● Выполнено: $doneThisMonth", color = Fc.Lime, style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { monthValue = YearMonth.now().toString(); selectedEpoch = today.toEpochDay() }, contentPadding = PaddingValues(0.dp)) { Text("Сегодня", color = Fc.Muted, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        state.active.forEach { draft -> item(key = "active-${draft.scheduleId}") {
            FcCard(border = Fc.Lime) {
                Text("Незавершённая тренировка", color = Fc.Lime)
                Text(draft.title, color = Fc.Text)
                ActionButton("Продолжить тренировку", { onStart(draft.scheduleId) })
            }
        } }
        item { Text(calendarDate(selectedEpoch) + if (selectedDate == today) " · сегодня" else "", color = Fc.Text, style = MaterialTheme.typography.titleMedium) }
        if (plans.isEmpty() && logs.isEmpty()) item {
            FcCard {
                Text("На этот день тренировок нет", color = Fc.Text, style = MaterialTheme.typography.titleMedium)
                Text("Можно выбрать программу или составить свой план.", color = Fc.Muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
        plans.filter { plan -> logs.none { it.session.scheduleId == plan.id } }.forEach { plan ->
            item(key = "plan-${plan.id}") {
                FcCard(modifier = Modifier.fillMaxWidth().combinedClickable(
                    onClick = { onEditPlan(selectedEpoch, plan.id) },
                    onLongClick = { deleting = false to plan.id }), border = Fc.Lime.copy(alpha = .55f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill("Запланировано", Fc.LimeSoft, Fc.Lime)
                        Spacer(Modifier.weight(1f))
                        Text(scheduleTime(plan.startMinute), color = Fc.Lime, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(plan.title, style = MaterialTheme.typography.headlineSmall, color = Fc.Text, modifier = Modifier.padding(top = 12.dp))
                    Text("${plan.items.size} ${com.fitclub.tracker.ui.programs.pluralRu(plan.items.size, "упражнение", "упражнения", "упражнений")} · ${plan.items.sumOf { it.sets }} ${com.fitclub.tracker.ui.programs.pluralRu(plan.items.sumOf { it.sets }, "подход", "подхода", "подходов")}", color = Fc.Muted, modifier = Modifier.padding(vertical = 8.dp))
                    plan.items.take(3).forEach { item ->
                        Text(state.catalog[item.exerciseId]?.name ?: item.exerciseId, color = Fc.Text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    ActionButton("Начать тренировку", { onStart(plan.id) })
                    Spacer(Modifier.height(8.dp))
                    ActionButton("Изменить план", { onEditPlan(selectedEpoch, plan.id) }, outlined = true)
                    TextButton(onClick = { deleting = false to plan.id }) { Text("Удалить план", color = Fc.Pink) }
                }
            }
        }
        logs.forEach { log ->
            item(key = "log-${log.session.id}") {
                FcCard(modifier = Modifier.fillMaxWidth().combinedClickable(
                    onClick = { onResult(log.session.id) }, onLongClick = { deleting = true to log.session.id })) {
                    Pill(log.status, Fc.LimeSoft, Fc.Lime)
                    Text(log.session.title.ifBlank { "Тренировка" },
                        color = Fc.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    Text("${log.completedSets} ${com.fitclub.tracker.ui.programs.pluralRu(log.completedSets, "подход", "подхода", "подходов")} · ${log.durationMin} мин", color = Fc.Muted, modifier = Modifier.padding(vertical = 8.dp))
                    Text("Общий тоннаж: ${units.format(log.volumeKg)}", color = Fc.Lime, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    ActionButton("Посмотреть результат", { onResult(log.session.id) }, outlined = true)
                    TextButton(onClick = { deleting = true to log.session.id }) { Text("Удалить результат", color = Fc.Pink) }
                }
            }
        }
        item { ActionButton(if (plans.isNotEmpty() || logs.isNotEmpty()) "+ Запланировать ещё тренировку" else "+ Запланировать тренировку", { onPlan(selectedEpoch) }) }
        if (logs.isNotEmpty()) item {
            FcCard {
                Text("Результат за этот день", color = Fc.Text, style = MaterialTheme.typography.titleMedium)
                Text("${logs.sumOf { it.completedSets }} ${com.fitclub.tracker.ui.programs.pluralRu(logs.sumOf { it.completedSets }, "подход", "подхода", "подходов")} · ${logs.sumOf { it.durationMin }} мин", color = Fc.Muted)
                Text("Общий тоннаж: ${units.format(logs.sumOf { it.volumeKg.toDouble() }.toFloat())}", color = Fc.Lime)
            }
        }
        if (error != null) item { Text(error.orEmpty(), color = Fc.Pink) }
    }
}

