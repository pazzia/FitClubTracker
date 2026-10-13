package com.fitclub.tracker.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.widthIn
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
import com.fitclub.tracker.ui.programs.pluralRu
import com.fitclub.tracker.ui.components.ActionButton
import com.fitclub.tracker.ui.components.Eyebrow
import com.fitclub.tracker.ui.components.FcCard
import com.fitclub.tracker.ui.components.LineIcon
import com.fitclub.tracker.ui.components.LineIcons
import com.fitclub.tracker.ui.components.NumberText
import com.fitclub.tracker.ui.components.Pill
import com.fitclub.tracker.ui.components.PrimaryButton
import com.fitclub.tracker.ui.components.PackIcons
import com.fitclub.tracker.ui.components.PillShape
import com.fitclub.tracker.ui.components.FilledIcon
import com.fitclub.tracker.ui.components.GlowProgress
import com.fitclub.tracker.ui.components.StatRow
import com.fitclub.tracker.ui.components.StatItem
import com.fitclub.tracker.ui.components.glow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
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

    LazyColumn(Modifier.fillMaxSize().testTag("today"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Header(today, profile, weekStreak(s.workouts, today), onProfile) }
        s.active.forEach { draft ->
            item(key = "active-${draft.scheduleId}") {
                FcCard(border = Fc.Lime) {
                    Eyebrow("Незавершённая тренировка", color = Fc.Lime)
                    Text(draft.title.ifBlank { "Тренировка" }, style = MaterialTheme.typography.headlineMedium, color = Fc.Text,
                        modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
                    PrimaryButton("Продолжить тренировку", { onStart(draft.scheduleId) }, icon = PackIcons.PLAY_FILL)
                }
            }
        }
        if (doneToday != null && todayPlan == null) item {
            FcCard(Modifier.fillMaxWidth().testTag("today-done")) {
                Pill("Сегодня выполнено", Fc.LimeSoft, Fc.Lime)
                Text(doneToday.session.title.ifBlank { "Тренировка" }, style = MaterialTheme.typography.displaySmall, color = Fc.Text, modifier = Modifier.padding(top = 8.dp))
                FactsRow(listOf("${doneToday.completedSets}" to pluralRu(doneToday.completedSets, "подход", "подхода", "подходов"),
                    "${doneToday.durationMin}" to "мин", units.number(units.fromKg(doneToday.volumeKg)) to "${units.label} объём"),
                    Modifier.padding(top = 6.dp, bottom = 14.dp))
                ActionButton("Посмотреть итог", { onResult(doneToday.session.id) }, outlined = true)
            }
        }
        item {
            when {
                todayPlan != null -> HeroCard(
                    label = "План на сегодня", title = todayPlan.title, items = todayPlan.items, s = s, units = units,
                    action = "Начать тренировку", enabled = true, onAction = { onStart(todayPlan.id) },
                    onEdit = { onEditPlan(todayEpoch, todayPlan.id) },
                    week = program?.takeIf { it.id == todayPlan.programId }?.let { workoutsThisWeek(s.workouts, today) to it.freqPerWeek }
                )
                program != null && nextDay != null -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val status = dayStatus(program, s.workouts, today)
                    val nextPlanned = s.plans.filter { it.epochDay > todayEpoch && it.id !in completedPlanIds }.minByOrNull { it.epochDay }
                    if (doneToday == null) StatusCard(status, program, s.workouts, today, nextPlanned?.epochDay)
                    SuggestionCard(
                        eyebrow = if (doneToday != null || status != DayStatus.OPEN) "Хотите потренироваться вне плана?" else "Следующая тренировка программы",
                        title = "День ${'A' + nextDay} · ${program.name}",
                        items = program.days[nextDay].items, s = s, starting = starting,
                        onStart = { vm.quickStart(program, nextDay, onStart) }, onPlan = { onPlan(todayEpoch) })
                }
                s.loaded -> FcCard {
                    Eyebrow("На сегодня тренировок нет")
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
        item { WeekStrip(today, s, program, onCalendar) }
        item {
            val goal = program?.freqPerWeek ?: 3
            StatRow(listOf(
                StatItem("${weekStreak(s.workouts, today)} нед.", "серия", PackIcons.FLAME),
                StatItem("${workoutsThisWeek(s.workouts, today)} / $goal", "за неделю", PackIcons.CALENDAR),
                StatItem(compactVolume(volumeThisWeek(s.workouts, today), units), "объём", PackIcons.PLATES)
            ))
        }
    }
}

@Composable
private fun Header(today: LocalDate, profile: ProfileSettings, streak: Int, onProfile: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Сегодня", style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 44.sp), color = Fc.Text, modifier = Modifier.weight(1f))
            // Avatar with the lime ring opens the profile.
            Box(
                Modifier.size(48.dp).clip(CircleShape).border(2.dp, Fc.Lime, CircleShape).padding(4.dp).clip(CircleShape).background(Fc.Surface2)
                    .semantics { contentDescription = "Профиль" }.clickable(role = Role.Button, onClick = onProfile),
                contentAlignment = Alignment.Center
            ) { Text(initials(profile.name), color = Fc.Text, style = MaterialTheme.typography.titleMedium) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                val name = profile.name.takeIf { it.isNotBlank() && it != "Мой профиль" }
                Text(name ?: today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru"))).replaceFirstChar { it.uppercase() },
                    color = Fc.Text, style = MaterialTheme.typography.titleMedium)
                Text(if (name != null) today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru"))).replaceFirstChar { it.uppercase() }
                    else "Хорошего дня для тренировки", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (streak > 0) Row(
                Modifier.heightIn(min = 40.dp).clip(PillShape).background(Fc.LimeSoft.copy(alpha = if (Fc.isLight) .35f else .5f))
                    .border(1.dp, Fc.Lime.copy(alpha = .6f), PillShape).padding(start = 10.dp, end = 14.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledIcon(PackIcons.FLAME_FILL, Fc.Lime, Modifier.size(18.dp))
                Text("$streak нед. подряд", color = Fc.Text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun WeekStrip(today: LocalDate, s: TodayState, program: ProgramDraft?, onCalendar: (Long) -> Unit) {
    val monday = weekStart(today)
    val done = s.workouts.filter { it.completedSets > 0 }.map { it.session.epochDay }.toSet()
    val planned = s.plans.map { it.epochDay }.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("План недели", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, lineHeight = 28.sp), color = Fc.Text)
                Eyebrow("Выполнено ${done.count { it in monday.toEpochDay()..monday.toEpochDay() + 6 }} из ${program?.freqPerWeek ?: 3}")
            }
            TextButton(onClick = { onCalendar(today.toEpochDay()) }) {
                Text("Календарь", color = Fc.Lime, style = MaterialTheme.typography.labelLarge)
                LineIcon(LineIcons.NEXT, Fc.Lime, Modifier.padding(start = 2.dp).size(16.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0 until 7).forEach { offset ->
                val date = monday.plusDays(offset.toLong())
                val epoch = date.toEpochDay()
                val isDone = epoch in done
                val isPlan = epoch in planned && !isDone
                val isToday = date == today
                val state = when { isDone -> "выполнено"; isPlan -> "запланировано"; else -> "без тренировки" }
                val shape = RoundedCornerShape(14.dp)
                val filled = isToday && (isDone || isPlan)
                Column(
                    Modifier.weight(1f).height(66.dp)
                        .glow(Fc.Lime, filled && !Fc.isLight, radius = 12.dp, alpha = .35f, corner = 14.dp)
                        .clip(shape)
                        .background(if (filled) Fc.Lime else Fc.Surface)
                        .border(if (isToday && !filled) 1.5.dp else 1.dp, if (isToday) Fc.Lime else Fc.Line, shape)
                        .semantics { contentDescription = "${weekdayLabels[offset]}, ${date.dayOfMonth}: $state" }
                        .clickable(role = Role.Button) { onCalendar(epoch) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    val fg = if (filled) Fc.OnAccent else if (isDone || isToday) Fc.Text else Fc.Muted
                    Text(weekdayLabels[offset], color = fg, style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.padding(top = 6.dp).size(20.dp), contentAlignment = Alignment.Center) {
                        when {
                            isDone -> LineIcon(LineIcons.CHECK, if (filled) Fc.OnAccent else Fc.Lime, Modifier.size(18.dp), strokeWidth = 2.6f)
                            isPlan -> LineIcon(PackIcons.DUMBBELL, if (filled) Fc.OnAccent else Fc.Lime, Modifier.size(18.dp), strokeWidth = 2.2f)
                            else -> Box(Modifier.size(14.dp).border(1.5.dp, if (isToday) Fc.Lime else Fc.Dim, CircleShape))
                        }
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
    action: String, enabled: Boolean, onAction: () -> Unit, onEdit: () -> Unit, week: Pair<Int, Int>? = null
) {
    val minutes = items.sumOf { it.sets * (45 + it.restSec) } / 60
    val photo = items.firstNotNullOfOrNull { com.fitclub.tracker.data.seed.FreeExerciseCatalog.media[it.exerciseId]?.images?.firstOrNull() }
    FcCard(Modifier.fillMaxWidth().testTag("today-hero")) {
        Box(Modifier.fillMaxWidth()) {
            if (photo != null) HeroPhoto(photo, Modifier.align(Alignment.TopEnd).padding(top = 0.dp).fillMaxWidth(.5f).height(168.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow(label, color = Fc.Lime, modifier = Modifier.weight(1f))
                    TextButton(onClick = onEdit) { Text("Изменить", color = Fc.Muted, style = MaterialTheme.typography.labelLarge) }
                }
                Text(title, style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp, lineHeight = 36.sp), color = Fc.Text,
                    modifier = Modifier.fillMaxWidth(if (photo != null) .62f else 1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (week != null) Row(Modifier.padding(top = 12.dp).fillMaxWidth(.62f), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberText("${week.first}/${week.second}", 16.sp)
                    GlowProgress(week.first.toFloat() / week.second.coerceAtLeast(1), Modifier.weight(1f))
                }
                Text("${items.size} ${pluralRu(items.size, "упражнение", "упражнения", "упражнений")} · ${items.sumOf { it.sets }} ${pluralRu(items.sumOf { it.sets }, "подход", "подхода", "подходов")} · ≈ $minutes мин",
                    color = Fc.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp).fillMaxWidth(if (photo != null) .6f else 1f))
            }
        }
        Spacer(Modifier.height(14.dp))
        items.take(3).forEach { item ->
            HorizontalDivider(color = Fc.Line2)
            Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.catalog[item.exerciseId]?.name ?: item.exerciseId, color = Fc.Text, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                NumberText(lastResult(s.workouts, item, units) ?: "${item.sets} × ${item.reps.label}", 16.sp, color = Fc.Muted,
                    modifier = Modifier.padding(start = 8.dp))
            }
        }
        val hasHistory = items.take(3).any { lastResult(s.workouts, it, units) != null }
        if (items.size > 3 || hasHistory) {
            HorizontalDivider(color = Fc.Line2)
            Text(listOfNotNull(if (items.size > 3) "и ещё ${items.size - 3}" else null, if (hasHistory) "справа — прошлый результат" else null).joinToString(" · "),
                color = Fc.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(action, { if (enabled) onAction() }, Modifier.testTag("today-start"), icon = PackIcons.PLAY_FILL)
    }
}

/** Program photo on the right of the hero card, fading into the card on its left edge. */
@Composable
private fun HeroPhoto(path: String, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, path) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                context.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }) }
            }.getOrNull()
        }
    }
    val surface = Fc.Surface
    bitmap?.let {
        Box(modifier.clip(RoundedCornerShape(16.dp))) {
            androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alpha = if (Fc.isLight) 1f else .85f)
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(surface, surface.copy(alpha = .55f), Color.Transparent))))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, surface.copy(alpha = .9f)))))
        }
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

enum class DayStatus { OPEN, REST, WEEK_DONE }

/** Rest after yesterday's workout for programs up to 4×/week; nothing more to do once the weekly goal is met. */
fun dayStatus(program: ProgramDraft, workouts: List<WorkoutLog>, today: LocalDate): DayStatus {
    if (workoutsThisWeek(workouts, today) >= program.freqPerWeek) return DayStatus.WEEK_DONE
    val yesterday = today.minusDays(1).toEpochDay()
    if (program.freqPerWeek <= 4 && workouts.any { it.completedSets > 0 && it.session.epochDay == yesterday }) return DayStatus.REST
    return DayStatus.OPEN
}

@Composable
private fun StatusCard(status: DayStatus, program: ProgramDraft, workouts: List<WorkoutLog>, today: LocalDate, nextPlanned: Long?) {
    val left = (program.freqPerWeek - workoutsThisWeek(workouts, today)).coerceAtLeast(0)
    val next = nextPlanned?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru"))) }
    val (title, text) = when (status) {
        DayStatus.REST -> "День отдыха" to "На сегодня тренировок нет, вчера была тренировка. " +
            (next?.let { "Следующая по плану — $it." } ?: "По программе «${program.name}» можно продолжить завтра.")
        DayStatus.WEEK_DONE -> "Цель недели выполнена" to "${program.freqPerWeek} из ${program.freqPerWeek} тренировок по «${program.name}». Время восстановиться."
        DayStatus.OPEN -> "На сегодня тренировок нет" to "По «${program.name}» на этой неделе осталось $left из ${program.freqPerWeek}." +
            (next?.let { " Ближайшая по плану — $it." } ?: "")
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Fc.Surface).border(1.dp, Fc.Line2, RoundedCornerShape(24.dp)).padding(18.dp)
        .testTag("today-status"), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Fc.Surface2), contentAlignment = Alignment.Center) {
            LineIcon(if (status == DayStatus.WEEK_DONE) LineIcons.CHECK else "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z", Fc.Text)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Fc.Text, style = MaterialTheme.typography.headlineMedium)
            Text(text, color = Fc.Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A secondary offer: the next program day is available, not prescribed. */
@Composable
private fun SuggestionCard(eyebrow: String, title: String, items: List<ItemDraft>, s: TodayState, starting: Boolean, onStart: () -> Unit, onPlan: () -> Unit) {
    val line = Fc.Line
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).drawBehind {
        drawRoundRect(line, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()))
    }.padding(18.dp).testTag("today-suggestion"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(eyebrow)
        Text(title, color = Fc.Text, style = MaterialTheme.typography.titleLarge)
        val names = items.mapNotNull { s.catalog[it.exerciseId]?.name }
        val minutes = items.sumOf { it.sets * (45 + it.restSec) } / 60
        Text(names.take(3).joinToString(", ") + (if (names.size > 3) " и ещё ${names.size - 3}" else "") + " · ≈ $minutes мин",
            color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(if (starting) "Готовлю…" else "Начать сейчас", { if (!starting) onStart() }, Modifier.weight(1f).testTag("today-start"), outlined = true)
            ActionButton("Запланировать", onPlan, Modifier.weight(1f), outlined = true)
        }
    }
}

/** Key numbers side by side: the number reads first, its label under it — instead of «a · b · c». */
@Composable
private fun FactsRow(facts: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        facts.forEach { (value, label) ->
            Column {
                NumberText(value, 22.sp)
                Text(label, color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
