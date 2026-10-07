package com.fitclub.tracker.ui.today

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitclub.tracker.AppContainer
import com.fitclub.tracker.data.db.ActiveWorkoutEntity
import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.model.RepRange
import com.fitclub.tracker.data.repo.ItemDraft
import com.fitclub.tracker.data.repo.ProfileSettings
import com.fitclub.tracker.data.repo.ProgramDraft
import com.fitclub.tracker.data.repo.ScheduledDraft
import com.fitclub.tracker.data.repo.WorkoutLog
import com.fitclub.tracker.domain.LocalWeightUnits
import com.fitclub.tracker.domain.WeightUnits
import com.fitclub.tracker.domain.nextProgramDay
import com.fitclub.tracker.domain.previousWorkingSet
import com.fitclub.tracker.domain.volumeThisWeek
import com.fitclub.tracker.domain.weekStart
import com.fitclub.tracker.domain.weekStreak
import com.fitclub.tracker.domain.workoutsThisWeek
import com.fitclub.tracker.ui.components.ActionButton
import com.fitclub.tracker.ui.components.Eyebrow
import com.fitclub.tracker.ui.components.FcCard
import com.fitclub.tracker.ui.components.NumberText
import com.fitclub.tracker.ui.components.Pill
import com.fitclub.tracker.ui.components.PrimaryButton
import com.fitclub.tracker.ui.navigation.appViewModel
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TodayState(
    val loaded: Boolean = false,
    val program: ProgramDraft? = null,
    val plans: List<ScheduledDraft> = emptyList(),
    val workouts: List<WorkoutLog> = emptyList(),
    val active: List<ActiveWorkoutEntity> = emptyList(),
    val catalog: Map<String, ExerciseEntity> = emptyMap()
)

class TodayViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.profile.settings
    val starting = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val state = combine(c.programs.observeActive(), c.schedule.observeAll(), c.fitness.observeWorkouts(),
        c.fitness.observeActive(), c.exercises.observeAll()) { program, plans, workouts, active, exercises ->
        TodayState(true, program, plans, workouts, active, exercises.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    /** Creates today's plan from the program day and hands back its id to open the workout. */
    fun quickStart(program: ProgramDraft, day: Int, onReady: (Long) -> Unit) {
        if (starting.value) return
        starting.value = true
        viewModelScope.launch {
            runCatching {
                val now = LocalTime.now()
                c.schedule.save(ScheduledDraft(epochDay = LocalDate.now().toEpochDay(), startMinute = now.hour * 60 + now.minute)
                    .fromProgram(program, day))
            }.onSuccess { error.value = null; onReady(it) }
                .onFailure { error.value = "Не удалось начать тренировку: ${it.message}" }
            starting.value = false
        }
    }
}

private val weekdayLabels = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("").ifBlank { "Я" }

@Composable
fun TodayScreen(
    onStart: (Long) -> Unit,
    onPlan: (Long) -> Unit,
    onEditPlan: (Long, Long) -> Unit,
    onCalendar: (Long) -> Unit,
    onPrograms: () -> Unit,
    onProfile: () -> Unit,
    onResult: (Long) -> Unit = {}
) {
    val vm = appViewModel { TodayViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val profile by vm.settings.collectAsStateWithLifecycle()
    val starting by vm.starting.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val units = LocalWeightUnits.current
    val today = LocalDate.now()
    val todayEpoch = today.toEpochDay()
    val completedPlanIds = s.workouts.mapNotNull { it.session.scheduleId }.toSet()
    val todayPlan = s.plans.filter { it.epochDay == todayEpoch && it.id !in completedPlanIds && s.active.none { a -> a.scheduleId == it.id } }
        .minByOrNull { it.startMinute }
    val program = s.program
    val nextDay = program?.let { nextProgramDay(it, s.workouts) }
    val doneToday = s.workouts.filter { it.session.epochDay == todayEpoch && it.completedSets > 0 }.maxByOrNull { it.session.startedAt }

    LazyColumn(Modifier.fillMaxSize().testTag("today"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Header(today, profile, onProfile) }
        s.active.forEach { draft ->
            item(key = "active-${draft.scheduleId}") {
                FcCard(border = Fc.Lime) {
                    Eyebrow("Незавершённая тренировка", color = Fc.Lime)
                    Text(draft.title.ifBlank { "Тренировка" }, style = MaterialTheme.typography.titleLarge, color = Fc.Text,
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
                    PrimaryButton("Продолжить тренировку", { onStart(draft.scheduleId) })
                }
            }
        }
        item { WeekStrip(today, s, program, onCalendar) }
        if (doneToday != null && todayPlan == null) item {
            FcCard(Modifier.fillMaxWidth().testTag("today-done")) {
                Pill("Сегодня выполнено", Fc.LimeSoft, Fc.Lime)
                Text(doneToday.session.title.ifBlank { "Тренировка" }, style = MaterialTheme.typography.displaySmall, color = Fc.Text, modifier = Modifier.padding(top = 8.dp))
                Text("${doneToday.completedSets} подходов · ${doneToday.durationMin} мин · ${units.format(doneToday.volumeKg)}", color = Fc.Muted,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
                ActionButton("Посмотреть итог", { onResult(doneToday.session.id) }, outlined = true)
            }
        }
        item {
            when {
                todayPlan != null -> HeroCard(
                    label = "План на сегодня", title = todayPlan.title, items = todayPlan.items, s = s, units = units,
                    action = "Начать тренировку", enabled = true, onAction = { onStart(todayPlan.id) },
                    onEdit = { onEditPlan(todayEpoch, todayPlan.id) }
                )
                program != null && nextDay != null -> HeroCard(
                    label = (if (doneToday != null) "Следующая · " else "") + "День ${'A' + nextDay} · ${program.name}", title = program.days[nextDay].items
                        .mapNotNull { s.catalog[it.exerciseId]?.group?.title }.distinct().take(2).joinToString(" + ").ifBlank { "Тренировка" },
                    items = program.days[nextDay].items, s = s, units = units,
                    action = if (starting) "Готовлю тренировку…" else "Начать тренировку", enabled = !starting,
                    onAction = { vm.quickStart(program, nextDay, onStart) }, onEdit = { onPlan(todayEpoch) }
                )
                s.loaded -> FcCard {
                    Eyebrow("Сегодня")
                    Text("Нет активной программы", style = MaterialTheme.typography.titleLarge, color = Fc.Text, modifier = Modifier.padding(top = 6.dp))
                    Text("Выберите программу — её дни будут чередоваться сами. Или соберите тренировку на сегодня вручную.",
                        color = Fc.Muted, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
                    PrimaryButton("Выбрать программу", onPrograms)
                    Spacer(Modifier.height(8.dp))
                    ActionButton("Собрать тренировку на сегодня", { onPlan(todayEpoch) }, outlined = true)
                }
            }
        }
        if (error != null) item { Text(error.orEmpty(), color = Fc.Danger) }
        item {
            val goal = program?.freqPerWeek ?: 3
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Серия", "${weekStreak(s.workouts, today)} нед.", Modifier.weight(1f))
                Stat("За неделю", "${workoutsThisWeek(s.workouts, today)} / $goal", Modifier.weight(1f))
                Stat("Объём", compactVolume(volumeThisWeek(s.workouts, today), units), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Header(today: LocalDate, profile: ProfileSettings, onProfile: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru"))).replaceFirstChar { it.uppercase() },
                color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
            Text("Сегодня", style = MaterialTheme.typography.displaySmall, color = Fc.Text)
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Fc.Surface).border(1.dp, Fc.Line, CircleShape)
                .semantics { contentDescription = "Профиль" }.clickable(role = Role.Button, onClick = onProfile),
            contentAlignment = Alignment.Center
        ) { Text(initials(profile.name), color = Fc.Text, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun WeekStrip(today: LocalDate, s: TodayState, program: ProgramDraft?, onCalendar: (Long) -> Unit) {
    val monday = weekStart(today)
    val done = s.workouts.filter { it.completedSets > 0 }.map { it.session.epochDay }.toSet()
    val planned = s.plans.map { it.epochDay }.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Неделя · ${done.count { it in monday.toEpochDay()..monday.toEpochDay() + 6 }} из ${program?.freqPerWeek ?: 3}", Modifier.weight(1f))
            TextButton(onClick = { onCalendar(today.toEpochDay()) }) { Text("Календарь", color = Fc.Text, style = MaterialTheme.typography.labelLarge) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0 until 7).forEach { offset ->
                val date = monday.plusDays(offset.toLong())
                val epoch = date.toEpochDay()
                val isDone = epoch in done
                val isPlan = epoch in planned && !isDone
                val isToday = date == today
                val state = when { isDone -> "выполнено"; isPlan -> "запланировано"; else -> "без тренировки" }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .semantics { contentDescription = "${weekdayLabels[offset]}, ${date.dayOfMonth}: $state" }
                        .clickable(role = Role.Button) { onCalendar(epoch) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(weekdayLabels[offset], color = Fc.Muted, style = MaterialTheme.typography.labelSmall)
                    val shape = CircleShape
                    Box(
                        Modifier.size(40.dp).clip(shape)
                            .then(when {
                                isDone -> Modifier.background(Fc.Lime)
                                isToday -> Modifier.border(2.dp, Fc.Text, shape)
                                isPlan -> Modifier.dashedCircle(Fc.Lime)
                                else -> Modifier
                            }),
                        contentAlignment = Alignment.Center
                    ) {
                        NumberText("${date.dayOfMonth}", 16.sp, color = when {
                            isDone -> Fc.OnAccent; isPlan -> Fc.Lime; isToday -> Fc.Text; else -> Fc.Muted
                        })
                    }
                }
            }
        }
    }
}

private fun Modifier.dashedCircle(color: Color): Modifier = drawBehind {
    drawCircle(color, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(width = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))))
}

@Composable
private fun HeroCard(
    label: String, title: String, items: List<ItemDraft>, s: TodayState, units: WeightUnits,
    action: String, enabled: Boolean, onAction: () -> Unit, onEdit: () -> Unit
) {
    FcCard(Modifier.fillMaxWidth().testTag("today-hero")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(label, Fc.LimeSoft, Fc.Lime, Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text("Изменить", color = Fc.Muted, style = MaterialTheme.typography.labelLarge) }
        }
        Text(title, style = MaterialTheme.typography.displayMedium, color = Fc.Text, modifier = Modifier.padding(top = 4.dp))
        val minutes = items.sumOf { it.sets * (45 + it.restSec) } / 60
        Text("${items.size} упражнений · ${items.sumOf { it.sets }} подходов · ≈ $minutes мин", color = Fc.Muted,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
        items.take(3).forEach { item ->
            HorizontalDivider(color = Fc.Line2)
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.catalog[item.exerciseId]?.name ?: item.exerciseId, color = Fc.Text, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                NumberText(lastResult(s.workouts, item, units) ?: "${item.sets} × ${item.reps.label}", 16.sp, color = Fc.Muted,
                    modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (items.size > 3) {
            HorizontalDivider(color = Fc.Line2)
            Text("и ещё ${items.size - 3} · справа результат прошлой тренировки", color = Fc.Muted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(action, { if (enabled) onAction() }, Modifier.testTag("today-start"))
    }
}

/** First working set of the last session with this exercise, in display units. */
private fun lastResult(workouts: List<WorkoutLog>, item: ItemDraft, units: WeightUnits): String? {
    val timed = item.reps == RepRange.TIME
    val set = previousWorkingSet(workouts, item.exerciseId, timed, 0) ?: return null
    return when {
        timed -> "${set.reps} с"
        set.weightKg > 0f -> "${units.number(units.fromKg(set.weightKg))} × ${set.reps}"
        else -> "× ${set.reps}"
    }
}

private fun compactVolume(kg: Float, units: WeightUnits): String {
    val value = units.fromKg(kg)
    if (value < 1000f) return "${units.number(value)} ${units.label}"
    val thousands = String.format(Locale("ru"), "%.1f", value / 1000f).removeSuffix(",0")
    return if (units.kg) "$thousands т" else "${thousands}k lb"
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Fc.Surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Fc.Muted, style = MaterialTheme.typography.labelMedium)
        NumberText(value, 24.sp)
    }
}
