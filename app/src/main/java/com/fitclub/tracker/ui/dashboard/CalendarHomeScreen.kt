package com.fitclub.tracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
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
    val workouts: List<WorkoutLog> = emptyList()
)

class CalendarViewModel(c: AppContainer) : ViewModel() {
    val state = combine(c.schedule.observeAll(), c.exercises.observeAll(), c.fitness.observeWorkouts()) { p, e, w ->
        CalendarState(p, e.associateBy { it.id }, w)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())
}

fun calendarDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay)
    .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))
fun scheduleTime(minute: Int) = "%02d:%02d".format(Locale.US, minute / 60, minute % 60)

@Composable
fun CalendarHomeScreen(onPlan: (Long) -> Unit, onStart: (Long) -> Unit, onProfile: () -> Unit) {
    val vm = appViewModel { CalendarViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    var selectedEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthValue)
    val selectedDate = LocalDate.ofEpochDay(selectedEpoch)
    val plan = state.plans.firstOrNull { it.epochDay == selectedEpoch }
    val logs = state.workouts.filter { it.session.epochDay == selectedEpoch }
    val completed = state.workouts.map { it.session.epochDay }.toSet()
    val planned = state.plans.associateBy { it.epochDay }
    val doneThisMonth = completed.count { YearMonth.from(LocalDate.ofEpochDay(it)) == month }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ТВОЙ РИТМ ТРЕНИРОВОК", color = Fc.Muted, style = MaterialTheme.typography.labelSmall)
                    ScreenTitle("Календарь")
                }
                IconButton(onClick = onProfile, modifier = Modifier.background(Fc.Surface2, CircleShape)) {
                    Icon(Icons.Filled.Person, "Личный кабинет", tint = Fc.Lime)
                }
            }
        }
        item {
            FcCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { monthValue = month.minusMonths(1).toString() }) { Text("‹", color = Fc.Text, style = MaterialTheme.typography.headlineSmall) }
                    Text(month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))).replaceFirstChar { it.uppercase() },
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Fc.Text, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { monthValue = month.plusMonths(1).toString() }) { Text("›", color = Fc.Text, style = MaterialTheme.typography.headlineSmall) }
                }
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
                                val color = when { selected -> Fc.Lime; done -> Color(0xFF234536); hasPlan -> Fc.LimeSoft; else -> Color.Transparent }
                                Column(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                                    .background(color, RoundedCornerShape(10.dp))
                                    .then(if (date == today && !selected) Modifier.border(1.dp, Fc.Lime, RoundedCornerShape(10.dp)) else Modifier)
                                    .semantics { contentDescription = calendarDate(epoch) + when { done -> ", выполнено"; hasPlan -> ", тренировка запланирована"; else -> ", запланировать тренировку" } }
                                    .clickable(role = Role.Button) { selectedEpoch = epoch; onPlan(epoch) },
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text("$number", color = if (selected) Fc.Bg else if (hasPlan || done) Fc.Lime else Fc.Text,
                                        style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal)
                                    if (done || hasPlan) Box(Modifier.padding(top = 3.dp).size(4.dp).background(if (selected) Fc.Bg else if (done) Color(0xFF69C7A4) else Fc.Lime, CircleShape))
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("● План", color = Fc.Lime, style = MaterialTheme.typography.labelSmall)
                    Text("● Выполнено: $doneThisMonth", color = Color(0xFF69C7A4), style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { monthValue = YearMonth.now().toString(); selectedEpoch = today.toEpochDay() }, contentPadding = PaddingValues(0.dp)) { Text("Сегодня", color = Fc.Muted, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        item { Text(calendarDate(selectedEpoch) + if (selectedDate == today) " · сегодня" else "", color = Fc.Text, style = MaterialTheme.typography.titleMedium) }
        if (plan == null) item {
            FcCard {
                Text("Тренировка не запланирована", color = Fc.Text, style = MaterialTheme.typography.titleMedium)
                Text("Выберите день программы или составьте тренировку из упражнений.", color = Fc.Muted, modifier = Modifier.padding(vertical = 10.dp))
                PrimaryButton("+ Запланировать тренировку", { onPlan(selectedEpoch) })
            }
        } else item {
            FcCard(border = Fc.Lime.copy(alpha = .55f), onClick = { onPlan(selectedEpoch) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(if (state.workouts.any { it.session.scheduleId == plan.id }) "Выполнено" else "Запланировано", Fc.LimeSoft, Fc.Lime)
                    Spacer(Modifier.weight(1f))
                    Text(scheduleTime(plan.startMinute), color = Fc.Lime, style = MaterialTheme.typography.titleMedium)
                }
                Text(plan.title, style = MaterialTheme.typography.headlineSmall, color = Fc.Text, modifier = Modifier.padding(top = 14.dp))
                Text("${plan.items.size} упражнений · ${plan.items.sumOf { it.sets }} подходов", color = Fc.Muted, modifier = Modifier.padding(vertical = 8.dp))
                plan.items.take(4).forEach { item ->
                    Row(Modifier.padding(vertical = 5.dp)) {
                        Text(state.catalog[item.exerciseId]?.name ?: item.exerciseId, color = Fc.Text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("${item.sets} × ${item.reps.label}", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Открыть план →", { onPlan(selectedEpoch) })
                if (state.workouts.none { it.session.scheduleId == plan.id }) TextButton(onClick = { onStart(plan.id) }, modifier = Modifier.fillMaxWidth()) { Text("Начать тренировку", color = Fc.Lime) }
            }
        }
        if (logs.isNotEmpty()) item {
            FcCard {
                Text("Результат за этот день", color = Fc.Text, style = MaterialTheme.typography.titleMedium)
                logs.forEach { log ->
                    Text("${log.completedSets} подходов · ${log.volumeKg.toInt()} кг · ${log.durationMin} мин", color = Fc.Muted, modifier = Modifier.padding(top = 8.dp))
                    log.exercises.groupBy { it.exerciseId }.forEach { (exerciseId, times) ->
                        val sets = log.sets.filter { it.done && it.exerciseId == exerciseId }
                        val best = sets.maxWithOrNull(compareBy<com.fitclub.tracker.data.db.SetLogEntity> { it.weightKg }.thenBy { it.reps })
                        val previous = state.workouts.filter { it.session.startedAt < log.session.startedAt }.flatMap { it.sets }
                            .filter { it.done && it.exerciseId == exerciseId && it.isTimed == best?.isTimed }
                            .maxWithOrNull(compareBy<com.fitclub.tracker.data.db.SetLogEntity> { it.weightKg }.thenBy { it.reps })
                        val seconds = times.sumOf { it.durationMillis } / 1000
                        Text(state.catalog[exerciseId]?.name ?: exerciseId, color = Fc.Text, modifier = Modifier.padding(top = 12.dp))
                        Text("${if (times.all { it.completed }) "Завершено" else "Выполнено частично"} · ${sets.size} подходов · ${seconds / 60} мин ${seconds % 60} сек", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                        Text("Тоннаж: ${sets.filter { !it.isTimed }.sumOf { (it.weightKg * it.reps).toDouble() }.toInt()} кг", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                        if (best != null) {
                            Text("Самый тяжёлый: ${best.weightKg} кг × ${best.reps}${if (best.isTimed) " сек." else ""}", color = Fc.Text, style = MaterialTheme.typography.bodySmall)
                            if (previous != null) Text("До тренировки: ${previous.weightKg} кг × ${previous.reps} · разница ${best.weightKg - previous.weightKg} кг", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                            else Text("Первый результат в истории", color = Fc.Lime, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

