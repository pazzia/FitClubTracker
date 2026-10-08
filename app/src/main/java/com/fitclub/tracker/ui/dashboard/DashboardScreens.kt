package com.fitclub.tracker.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
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

@Composable
fun ProgressScreen(onProfile: () -> Unit) {
    val vm = appViewModel { DashboardViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val units = LocalWeightUnits.current
    var weeksBack by rememberSaveable { mutableStateOf(12) }
    var selectedExercise by rememberSaveable { mutableStateOf("") }
    val today = LocalDate.now()
    val latest = s.measurements.lastOrNull { it.weightKg != null }
    val first = s.measurements.firstOrNull { it.weightKg != null }
    val weekData = com.fitclub.tracker.domain.weeklyVolume(s.workouts, today, weeksBack)
    val weekDates = weekData.map { it.monday }
    val weekly = weekData.map { it.volumeKg }
    val thisWeek = weekly.lastOrNull() ?: 0f
    val lastWeek = weekly.getOrNull(weekly.lastIndex - 1) ?: 0f

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Прогресс", Modifier.weight(1f))
                CircleIconButton("Профиль", onProfile, filled = true) { Icon(Icons.Filled.Person, null, tint = Fc.Text) }
            }
        }
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
            FcCard(onClick = onProfile) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("Вес тела")
                        if (latest?.weightKg != null) NumberText(units.format(latest.weightKg), 28.sp) else Text("Нет замеров", style = MaterialTheme.typography.headlineSmall, color = Fc.Text)
                        if (latest?.weightKg != null && first?.weightKg != null) Text("${signed(units.fromKg(latest.weightKg - first.weightKg))} ${units.label} с первого замера", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Fc.Muted)
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
                    Text("${sets.size} подходов за выбранный период", color = Fc.Muted)
                    TextButton(onClick = { selectedExercise = id }) { Text("История упражнения", color = Fc.Lime) }
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
}

class ProfileViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.profile.settings
    val measurements = c.fitness.observeMeasurements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workouts = c.fitness.observeWorkouts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun update(value: ProfileSettings) = c.profile.update(value)
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    fun add(weight: Float?, waist: Float?, fat: Float?, chest: Float?, arm: Float?, onSaved: () -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            runCatching { c.fitness.addMeasurement(weight, waist, fat, chest, arm) }
                .onSuccess { error = null; onSaved() }.onFailure { error = "Не удалось сохранить замер: ${it.message}" }
            saving = false
        }
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val vm = appViewModel { ProfileViewModel(it) }
    val st by vm.settings.collectAsStateWithLifecycle()
    val measurements by vm.measurements.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var weight by rememberSaveable { mutableStateOf("") }; var waist by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }; var chest by rememberSaveable { mutableStateOf("") }; var arm by rememberSaveable { mutableStateOf("") }
    val latest = measurements.lastOrNull()
    val units = WeightUnits(st.unitsKg)
    var measurementUnitsKg by rememberSaveable { mutableStateOf(st.unitsKg) }
    val entryUnits = WeightUnits(measurementUnitsKg)
    val values = listOf(weight, waist, fat, chest, arm)
    val measurementValid = values.any { it.isNotBlank() } && values.mapIndexed { i, v -> v.isBlank() || num(v)?.let { n -> n.isFinite() && (if (i == 2) n >= 0 else n > 0) } == true }.all { it } && (fat.isBlank() || num(fat)?.let { it in 0f..100f } == true)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BackRow("Личный кабинет", onBack) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(68.dp).background(Fc.Lime, CircleShape), contentAlignment = Alignment.Center) { Text(st.name.split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("").ifBlank { "Я" }, style = MaterialTheme.typography.headlineSmall, color = Fc.OnAccent) }
                Column { ScreenTitle(st.name); Text("${if (st.level == 1) "Новичок" else if (st.level == 2) "Продвинутый" else "Профи"}", color = Fc.Muted) }
            }
        }
        item {
            FcCard {
                Text("О СЕБЕ", style = MaterialTheme.typography.labelMedium, color = Fc.Muted)
                OutlinedTextField(st.name, { vm.update(st.copy(name = it)) }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Пол", color = Fc.Text, modifier = Modifier.padding(top = 10.dp)); Segmented(Sex.entries, st.sex, { vm.update(st.copy(sex = it)) }, { it.title })
                Text("Уровень подготовки", color = Fc.Text, modifier = Modifier.padding(top = 12.dp)); Segmented(listOf(1,2,3), st.level, { vm.update(st.copy(level = it)) }, { if (it==1) "Новичок" else if (it==2) "Продв." else "Профи" })
                Text("Пол меняет модель тела в справочнике и отчётах.", color = Fc.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item { FcCard { Text("Модель тела", style = MaterialTheme.typography.titleSmall, color = Fc.Text); Spacer(Modifier.height(12.dp)); BodyPair(st.sex) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("Тренировок", "${workouts.count { it.completedSets > 0 }}", "выполнено", Modifier.weight(1f)); MiniStat("Замеров", "${measurements.size}", "параметров", Modifier.weight(1f))
        } }
        item {
            FcCard {
                Text("ПАРАМЕТРЫ ТЕЛА", style = MaterialTheme.typography.labelMedium, color = Fc.Muted)
                Spacer(Modifier.height(8.dp))
                listOf("Вес" to latest?.weightKg?.let { units.format(it) }, "Талия" to latest?.waistCm?.let { "${fmt(it)} см" }, "Жир" to latest?.fatPercent?.let { "${fmt(it)}%" }, "Грудь" to latest?.chestCm?.let { "${fmt(it)} см" }, "Рука" to latest?.armCm?.let { "${fmt(it)} см" }).forEach { (k,v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Text(k, color = Fc.Muted, modifier = Modifier.weight(1f)); Text(v ?: "—", color = Fc.Text) }
                }
                Spacer(Modifier.height(8.dp)); PrimaryButton("Добавить замер", { measurementUnitsKg = st.unitsKg; adding = true })
            }
        }
        if (adding) item {
            FcCard(border = Fc.Lime.copy(.5f)) {
                Text("НОВЫЙ ЗАМЕР · СЕГОДНЯ", style = MaterialTheme.typography.titleMedium, color = Fc.Text)
                MeasureField("Вес, ${entryUnits.label}", weight) { weight = it }; MeasureField("Талия, см", waist) { waist = it }; MeasureField("Жир, %", fat) { fat = it }; MeasureField("Грудь, см", chest) { chest = it }; MeasureField("Рука, см", arm) { arm = it }
                if (!measurementValid) Text("Введите хотя бы один положительный замер. Жир — от 0 до 100%.", color = Fc.Muted)
                if (vm.error != null) Text(vm.error.orEmpty(), color = Fc.Pink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { adding = false }, modifier = Modifier.weight(1f)) { Text("Отмена", color = Fc.Muted) }
                    Button(onClick = { vm.add(num(weight)?.let(entryUnits::toKg), num(waist), num(fat), num(chest), num(arm)) { adding = false; weight = ""; waist = ""; fat = ""; chest = ""; arm = "" } }, enabled = measurementValid && !vm.saving, colors = ButtonDefaults.buttonColors(containerColor = Fc.Lime), modifier = Modifier.weight(1f)) { Text(if (vm.saving) "Сохраняю…" else "Сохранить", color = Fc.OnAccent) }
                }
            }
        }
        item {
            FcCard {
                Text("ОФОРМЛЕНИЕ", style = MaterialTheme.typography.labelMedium, color = Fc.Muted)
                listOf("Светлая", "Темная").forEach { name ->
                    Row(Modifier.fillMaxWidth().clickable { vm.update(st.copy(theme = name)) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).background(if (name == "Светлая") Color(0xFFF3F4EF) else Color(0xFF0A0C0D), CircleShape).border(1.dp, Fc.Line, CircleShape))
                        Text(name, color = Fc.Text, modifier = Modifier.padding(start = 10.dp).weight(1f))
                        if (st.theme == name) Icon(Icons.Filled.Check, null, tint = Fc.Lime)
                    }
                }
            }
        }
        item {
            FcCard {
                Text("НАСТРОЙКИ", color = Fc.Muted, style = MaterialTheme.typography.labelMedium)
                Text("Единицы веса", color = Fc.Text, modifier = Modifier.padding(vertical = 8.dp))
                Segmented(listOf(true, false), st.unitsKg, { vm.update(st.copy(unitsKg = it)) }, { if (it) "Килограммы (кг)" else "Фунты (lb)" }, selectedBg = Fc.Lime)
                Text("История, тоннаж и замеры отображаются в выбранных единицах. Активная тренировка и открытый редактор сохраняют единицы ввода до завершения.", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Fc.Line)
                Text("Версия приложения: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Fc.Text)
                Text(if (BuildConfig.APPLICATION_ID.endsWith(".experimental")) "Тестовая сборка" else "Основная версия", color = Fc.Muted)
                VersionDetails()
            }
        }
    }
}

@Composable private fun MeasureField(label: String, value: String, onChange: (String) -> Unit) { OutlinedTextField(value, { onChange(com.fitclub.tracker.domain.normalizeDecimalInput(it)) }, label = { Text(label) }, singleLine = true, isError = value.isNotBlank() && num(value)?.let { it.isFinite() && it >= 0 } != true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Fc.Text, unfocusedTextColor = Fc.Text, focusedBorderColor = Fc.Lime, unfocusedBorderColor = Fc.Line)) }
private fun num(s: String) = s.replace(',', '.').toFloatOrNull()
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
            Row { Text(k, color = Fc.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(150.dp)); Text(v, color = Fc.Text, style = MaterialTheme.typography.bodySmall) }
        }
        TextButton(onClick = {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString("FitClub\n" + lines.joinToString("\n") { "${it.first}: ${it.second}" }))
            copied = true
        }, contentPadding = PaddingValues(0.dp)) { Text(if (copied) "Скопировано" else "Скопировать информацию о сборке", color = Fc.Lime) }
    }
}
