package com.fitclub.tracker.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
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
import com.fitclub.tracker.BuildConfig
import com.fitclub.tracker.domain.LocalWeightUnits
import com.fitclub.tracker.domain.WeightUnits
import com.fitclub.tracker.domain.bestWorkingSet
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
    val settings = c.profile.settings
    val state = combine(
        c.programs.observeActive(), c.exercises.observeAll(), c.fitness.observeWorkouts(), c.fitness.observeMeasurements()
    ) { program, exercises, workouts, measurements ->
        DashboardState(program, exercises.associateBy { it.id }, workouts, measurements)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())
}

@Composable private fun MiniStat(title: String, value: String, note: String, modifier: Modifier) {
    FcCard(modifier) {
        Text(title, color = Fc.Muted, style = MaterialTheme.typography.labelMedium)
        Text(value, color = Fc.Text, style = MaterialTheme.typography.headlineSmall)
        Text(note, color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

/** Sections of «Прогресс»: strength, the body, and regularity — each answers one question. */
enum class ProgressSection(val title: String) { STRENGTH("Сила"), BODY("Тело"), REGULARITY("Регулярность") }

@Composable
fun ProgressScreen(onProfile: () -> Unit, onMeasure: (String?) -> Unit = {}, initial: ProgressSection = ProgressSection.STRENGTH) {
    val vm = appViewModel { DashboardViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val units = LocalWeightUnits.current
    var section by rememberSaveable { mutableStateOf(initial) }
    var weeksBack by rememberSaveable { mutableStateOf(12) }
    var selectedExercise by rememberSaveable { mutableStateOf("") }
    val today = LocalDate.now()
    val weekData = com.fitclub.tracker.domain.weeklyVolume(s.workouts, today, weeksBack)
    val weekDates = weekData.map { it.monday }
    val weekly = weekData.map { it.volumeKg }
    val thisWeek = weekly.lastOrNull() ?: 0f
    val lastWeek = weekly.getOrNull(weekly.lastIndex - 1) ?: 0f

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Прогресс", Modifier.weight(1f))
            }
        }
        item { Segmented(ProgressSection.entries, section, { section = it }, { it.title }, selectedBg = Fc.Lime) }
        when (section) {
            ProgressSection.STRENGTH -> {
                item { Segmented(listOf(4, 12, 26), weeksBack, { weeksBack = it }, { when (it) { 4 -> "4 недели"; 12 -> "12 недель"; else -> "Полгода" } }) }
        item {
            FcCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("Объём за эту неделю", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                        NumberText(units.format(thisWeek), 40.sp)
                    }
                    if (lastWeek > 0f) {
                        val change = ((thisWeek - lastWeek) / lastWeek * 100).roundToInt()
                        Pill("${if (change > 0) "+" else ""}$change% к прошлой", if (change >= 0) Fc.LimeSoft else Fc.Surface2, if (change >= 0) Fc.Lime else Fc.Muted)
                    }
                }
                Spacer(Modifier.height(14.dp))
                val max = weekly.maxOrNull()?.coerceAtLeast(1f) ?: 1f
                Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(if (weekly.size > 12) 3.dp else 6.dp), verticalAlignment = Alignment.Bottom) {
                    weekly.forEachIndexed { i, v ->
                        Box(Modifier.weight(1f).height((112 * v / max).coerceAtLeast(3f).dp)
                            .background(if (i == weekly.lastIndex) Fc.Lime else Fc.Line, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp)))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(weekDates.first(), weekDates[weekDates.size / 2], weekDates.last()).forEach {
                        Text(it.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru"))), color = Fc.Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            Eyebrow("По упражнениям")
            val allIds = s.workouts.flatMap { it.sets }.map { it.exerciseId }.distinct().sortedBy { s.exercises[it]?.name }
            var exerciseMenu by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { exerciseMenu = true }) { Text(if (selectedExercise.isBlank()) "Все упражнения" else s.exercises[selectedExercise]?.name ?: selectedExercise, color = Fc.Lime) }
                DropdownMenu(expanded = exerciseMenu, onDismissRequest = { exerciseMenu = false }) {
                    DropdownMenuItem(text = { Text("Все упражнения") }, onClick = { selectedExercise = ""; exerciseMenu = false })
                    allIds.forEach { id -> DropdownMenuItem(text = { Text(s.exercises[id]?.name ?: id) }, onClick = { selectedExercise = id; exerciseMenu = false }) }
                }
            }
            val periodWorkouts = s.workouts.filter { it.session.epochDay in weekDates.first().toEpochDay()..weekDates.last().plusDays(6).toEpochDay() }
            val byExercise = periodWorkouts.flatMap { it.sets }
                .filter { it.done && !it.warmup && !it.assisted && (selectedExercise.isBlank() || selectedExercise == it.exerciseId) }
                .groupBy { it.exerciseId to it.isTimed }.entries
                .sortedWith(compareBy({ s.exercises[it.key.first]?.name ?: it.key.first }, { it.key.second }))
            if (byExercise.isEmpty()) Text("Завершите первую тренировку — здесь появятся результаты упражнений.", color = Fc.Muted, modifier = Modifier.padding(vertical = 14.dp))
            byExercise.forEach { (key, sets) ->
                val (id, timed) = key
                val best = bestWorkingSet(sets, id, timed)!!
                FcCard(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("progress-$id-$timed")) {
                    Text(s.exercises[id]?.name ?: id, color = Fc.Text, style = MaterialTheme.typography.titleSmall)
                    Text(if (timed) "На время · секунды" else "С повторениями · ${units.label}", color = Fc.Muted, style = MaterialTheme.typography.labelSmall)
                    Text(if (timed) "Самое долгое удержание: ${best.reps} сек." else "Самый тяжёлый подход: ${units.format(best.weightKg)} × ${best.reps}", color = Fc.Text, modifier = Modifier.padding(top = 6.dp))
                    Text("${sets.size} ${com.fitclub.tracker.ui.programs.pluralRu(sets.size, "подход", "подхода", "подходов")} за выбранный период", color = Fc.Muted)
                    TextButton(onClick = { selectedExercise = id }) { Text("История упражнения", color = Fc.Lime) }
                    if (selectedExercise == id && !timed) StrengthChart(com.fitclub.tracker.domain.weeklyOneRepMax(s.workouts, id, today, weeksBack), units)
                    if (selectedExercise == id) periodWorkouts.sortedBy { it.session.startedAt }.forEach { log ->
                        val performed = log.sets.filter { it.done && it.exerciseId == id && it.isTimed == timed && !it.warmup && !it.assisted }
                        if (performed.isNotEmpty()) {
                            Text(calendarDate(log.session.epochDay), color = Fc.Muted, style = MaterialTheme.typography.labelSmall)
                            performed.forEach { row -> Text(if (timed) "${row.reps} сек." else "${units.format(row.weightKg)} × ${row.reps}", color = Fc.Text) }
                        }
                    }
                }
            }
        }
            }
            ProgressSection.BODY -> item {
                com.fitclub.tracker.ui.profile.BodyModelSection(settings.sex, s.measurements, onMeasure = { onMeasure(it?.key) })
            }
            ProgressSection.REGULARITY -> {
        item {
            val doneDays = s.workouts.filter { it.completedSets > 0 }.map { it.session.epochDay }.toSet()
            val start = com.fitclub.tracker.domain.weekStart(today).minusWeeks(11)
            val streak = com.fitclub.tracker.domain.weekStreak(s.workouts, today)
            FcCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Регулярность", color = Fc.Text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    Text(if (streak > 0) "$streak нед. подряд" else "12 недель", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0 until 12).forEach { week ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            (0 until 7).forEach { day ->
                                val date = start.plusWeeks(week.toLong()).plusDays(day.toLong())
                                val shape = RoundedCornerShape(3.dp)
                                Box(Modifier.fillMaxWidth().height(12.dp).then(when {
                                    date.toEpochDay() in doneDays -> Modifier.background(Fc.Lime, shape)
                                    date.isAfter(today) -> Modifier
                                    else -> Modifier.background(Fc.Surface2, shape)
                                }))
                            }
                        }
                    }
                }
            }
        }
                item {
                    val done = s.workouts.filter { it.completedSets > 0 }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniStat("Тренировок", "${done.size}", "всего", Modifier.weight(1f))
                        MiniStat("За 4 недели", "${done.count { it.session.epochDay > today.minusWeeks(4).toEpochDay() }}", "тренировок", Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Best estimated 1RM per week for one exercise: the line shows whether strength grows. */
@Composable
private fun StrengthChart(points: List<com.fitclub.tracker.domain.StrengthPoint>, units: WeightUnits) {
    if (points.size < 2) {
        Text("График силы появится после двух недель с этим упражнением.", color = Fc.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    val values = points.map { units.fromKg(it.oneRepMaxKg) }
    val lo = values.min(); val hi = values.max()
    val change = values.last() - values.first()
    val line = Fc.DataBlue
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("strength-chart"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Оценка 1ПМ по неделям", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                NumberText(units.format(points.last().oneRepMaxKg), 24.sp)
            }
            Text("${signed(change)} ${units.label}", color = if (change >= 0) Fc.Good else Fc.Bad, style = MaterialTheme.typography.labelLarge)
        }
        Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = "Оценка 1ПМ: с ${fmt(values.first())} до ${fmt(values.last())} ${units.label}" }) {
            val pad = 6.dp.toPx()
            fun pt(i: Int) = Offset(pad + i * (size.width - 2 * pad) / (values.size - 1),
                size.height - pad - (if (hi == lo) .5f else (values[i] - lo) / (hi - lo)) * (size.height - 2 * pad))
            for (i in 1 until values.size) drawLine(line, pt(i - 1), pt(i), 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            values.indices.forEach { drawCircle(line, if (it == values.lastIndex) 5.dp.toPx() else 3.dp.toPx(), pt(it)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().monday.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru"))), color = Fc.Muted, style = MaterialTheme.typography.labelSmall)
            Text(points.last().monday.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru"))), color = Fc.Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

class ProfileViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.profile.settings
    val measurements = c.fitness.observeMeasurements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workouts = c.fitness.observeWorkouts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun update(value: ProfileSettings) = c.profile.update(value)
}

@Composable
fun ProfileScreen(onBack: (() -> Unit)?, onMeasure: (String?) -> Unit = {}, onBody: () -> Unit = {}) {
    val vm = appViewModel { ProfileViewModel(it) }
    val st by vm.settings.collectAsStateWithLifecycle()
    val measurements by vm.measurements.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (onBack != null) item { BackRow("Личный кабинет", onBack) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Avatar with the lime ring of the new design; initials stand in for a photo.
                val named = st.name.isNotBlank() && st.name != "Мой профиль"
                Box(Modifier.size(76.dp).border(2.dp, Fc.Lime, CircleShape).padding(5.dp).background(Fc.Surface2, CircleShape), contentAlignment = Alignment.Center) {
                    if (named) Text(st.name.split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString(""),
                        style = MaterialTheme.typography.headlineMedium, color = Fc.Text)
                    else LineIcon(PackIcons.USER, Fc.Text, Modifier.size(30.dp), strokeWidth = 1.8f)
                }
                Column(Modifier.weight(1f)) {
                    ScreenTitle(if (named) st.name else "Профиль")
                    Text(levelTitle(st.level), color = Fc.Muted)
                }
            }
        }
        item {
            FcCard {
                Text("О себе", style = MaterialTheme.typography.titleMedium, color = Fc.Text)
                // The stored default «Мой профиль» shows as an empty field with a hint.
                OutlinedTextField(if (st.name == "Мой профиль") "" else st.name, { vm.update(st.copy(name = it)) }, label = { Text("Имя") },
                    placeholder = { Text("Как вас зовут") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Пол", color = Fc.Text, modifier = Modifier.padding(top = 10.dp)); Segmented(Sex.entries, st.sex, { vm.update(st.copy(sex = it)) }, { it.title })
                Text("Уровень подготовки", color = Fc.Text, modifier = Modifier.padding(top = 12.dp))
                // The level decides how much the set card shows; the switch in Settings can override it.
                Segmented(listOf(1,2,3), st.level, { vm.update(st.copy(level = it, advancedSets = it > 1)) }, { levelTitle(it) })
                Text("Пол выбирает фигуру в замерах и в справочнике. Новичку тренировка показывает только вес, повторы и «Подход выполнен».", color = Fc.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            FcCard(Modifier.fillMaxWidth().testTag("profile-body"), onClick = onBody) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Замеры тела", style = MaterialTheme.typography.titleMedium, color = Fc.Text)
                        Text(measurements.lastOrNull()?.let { "Последний замер ${com.fitclub.tracker.ui.profile.shortDate(it.epochDay)}, всего ${measurements.size}" }
                            ?: "Вес, жир и обхваты на вашей фигуре", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Fc.Muted)
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("Тренировок", "${workouts.count { it.completedSets > 0 }}", "выполнено", Modifier.weight(1f)); MiniStat("Замеров", "${measurements.size}", "записано", Modifier.weight(1f))
        } }
        item {
            FcCard {
                Text("Оформление", style = MaterialTheme.typography.titleMedium, color = Fc.Text)
                // One control for the theme; the stored values stay «Светлая» / «Темная».
                Segmented(listOf("Темная", "Светлая"), st.theme, { vm.update(st.copy(theme = it)) }, { if (it == "Темная") "Тёмная" else it },
                    modifier = Modifier.padding(top = 8.dp), selectedBg = Fc.Lime)
            }
        }
        item {
            FcCard {
                Text("Настройки", color = Fc.Text, style = MaterialTheme.typography.titleMedium)
                Text("Единицы веса", color = Fc.Text, modifier = Modifier.padding(vertical = 8.dp))
                Segmented(listOf(true, false), st.unitsKg, { vm.update(st.copy(unitsKg = it)) }, { if (it) "Килограммы (кг)" else "Фунты (lb)" }, selectedBg = Fc.Lime)
                Text("История, тоннаж и замеры отображаются в выбранных единицах. Активная тренировка и открытый редактор сохраняют единицы ввода до завершения.", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Fc.Line)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(value = st.advancedSets, role = Role.Switch) { vm.update(st.copy(advancedSets = it)) }
                        .testTag("advanced-sets"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Подробный ввод подходов", color = Fc.Text)
                        Text("Разминка, дропсеты, суперсеты, RIR и помощь в подходе. Выключите, чтобы в тренировке остались только вес, повторы и «Подход выполнен». Техники из программы показываются всегда.",
                            color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = st.advancedSets, onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedTrackColor = Fc.Lime, checkedThumbColor = Fc.OnAccent))
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(value = st.restSound, role = Role.Switch) { vm.update(st.copy(restSound = it)); if (it) com.fitclub.tracker.ui.workout.RestSound.play() }
                        .testTag("rest-sound"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Звук в конце отдыха", color = Fc.Text)
                        Text("Короткий сигнал, когда время отдыха между подходами закончилось. Громкость — как у музыки, слышно и в наушниках. Вибрация остаётся всегда.",
                            color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = st.restSound, onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedTrackColor = Fc.Lime, checkedThumbColor = Fc.OnAccent))
                }
                if (st.restSound) TextButton(onClick = { com.fitclub.tracker.ui.workout.RestSound.play() }, contentPadding = PaddingValues(0.dp)) { Text("Проверить звук", color = Fc.Lime) }
                Spacer(Modifier.height(8.dp))
                SettingSwitch("Отсчёт 3-2-1 перед тренировкой", "Перед первым упражнением — короткий отсчёт с вибрацией. Нажатие на экран начинает сразу.",
                    st.startCountdown, Modifier.testTag("start-countdown")) { vm.update(st.copy(startCountdown = it)) }
                Spacer(Modifier.height(8.dp))
                SettingSwitch("Звуки старта и финиша", "Щелчок на «GO» и короткий сигнал, когда тренировка завершена.",
                    st.workoutSounds, Modifier.testTag("workout-sounds")) { vm.update(st.copy(workoutSounds = it)) }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Fc.Line)
                Text("Версия приложения: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Fc.Text)
                Text(if (BuildConfig.APPLICATION_ID.endsWith(".experimental")) "Тестовая сборка" else "Основная версия", color = Fc.Muted)
                VersionDetails()
            }
        }
    }
}

private fun fmt(v: Float) = if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else "%.1f".format(Locale.US, v).replace('.', ',')
private fun signed(v: Float) = (if (v > 0) "+" else "") + fmt(v)




/** Everything needed to identify a build in a bug report, plus the live screenshot protection state. */
fun buildInfoLines(secureFlag: Boolean?): List<Pair<String, String>> = listOf(
    "Версия" to "${BuildConfig.VERSION_NAME} (код ${BuildConfig.VERSION_CODE})",
    "Сборка CI" to if (BuildConfig.BUILD_NUMBER == "local") "локальная" else "№${BuildConfig.BUILD_NUMBER}",
    "Коммит" to BuildConfig.GIT_SHA,
    "Ветка" to BuildConfig.GIT_BRANCH,
    "Собрано" to BuildConfig.BUILD_TIME,
    "Пакет" to BuildConfig.APPLICATION_ID,
    "Схема базы" to "v${com.fitclub.tracker.data.db.DB_SCHEMA_VERSION}",
    "Устройство" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
    "Запрет снимков экрана" to when (secureFlag) { true -> "включён (FLAG_SECURE)"; false -> "выключен"; null -> "неизвестно" }
)

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun VersionDetails() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val secure = context.findActivity()?.window?.attributes?.flags?.let { it and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0 }
    val lines = buildInfoLines(secure)
    Column(Modifier.padding(top = 10.dp).testTag("version-details"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { (k, v) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(k, color = Fc.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.45f)); Text(v, color = Fc.Text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.55f)) }
        }
        TextButton(onClick = {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString("FitClub\n" + lines.joinToString("\n") { "${it.first}: ${it.second}" }))
            copied = true
        }, contentPadding = PaddingValues(0.dp)) { Text(if (copied) "Скопировано" else "Скопировать информацию о сборке", color = Fc.Lime) }
    }
}

@Composable
private fun SettingSwitch(title: String, note: String, value: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = value, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Fc.Text)
            Text(note, color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = value, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = Fc.Lime, checkedThumbColor = Fc.OnAccent))
    }
}

/** Level names used in the profile header and the level picker. */
internal fun levelTitle(level: Int) = when (level) { 1 -> "Новичок"; 2 -> "Опытный"; else -> "Профи" }
