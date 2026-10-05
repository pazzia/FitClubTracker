package com.fitclub.tracker.ui.programs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import com.fitclub.tracker.ui.components.ExerciseThumbnail
import com.fitclub.tracker.ui.components.ProgramPhotoButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitclub.tracker.AppContainer
import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.model.Goal
import com.fitclub.tracker.data.model.MuscleGroup
import com.fitclub.tracker.data.model.REST_OPTIONS
import com.fitclub.tracker.data.model.RepRange
import com.fitclub.tracker.data.model.restLabel
import com.fitclub.tracker.data.repo.DayDraft
import com.fitclub.tracker.data.repo.ItemDraft
import com.fitclub.tracker.data.repo.ProgramDraft
import com.fitclub.tracker.data.repo.ProgramIcons
import com.fitclub.tracker.domain.FitResult
import com.fitclub.tracker.domain.ProgramScorer
import com.fitclub.tracker.ui.components.BackRow
import com.fitclub.tracker.ui.components.DashedButton
import com.fitclub.tracker.ui.components.FcCard
import com.fitclub.tracker.ui.components.IconPaths
import com.fitclub.tracker.ui.components.Pill
import com.fitclub.tracker.ui.components.PrimaryButton
import com.fitclub.tracker.ui.components.ProgramCover
import com.fitclub.tracker.ui.components.ScreenTitle
import com.fitclub.tracker.ui.components.StrokeIcon
import com.fitclub.tracker.ui.navigation.appViewModel
import com.fitclub.tracker.ui.theme.Fc
import com.fitclub.tracker.ui.theme.foregroundFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val PROGRAM_COLORS = listOf(
    0xFFD4FF3A to "Лайм", 0xFFFF3D6E to "Розовый", 0xFFFFB23F to "Оранжевый",
    0xFF9B7BFF to "Фиолетовый", 0xFF3DE0FF to "Голубой", 0xFFF5F5F7 to "Белый"
)

private fun plural(n: Int, one: String, few: String, many: String): String {
    val t = n % 100; val u = n % 10
    return when { t in 11..19 -> many; u == 1 -> one; u in 2..4 -> few; else -> many }
}

// ---------- Список программ ----------

class ProgramsViewModel(private val c: AppContainer) : ViewModel() {
    val programs: StateFlow<List<ProgramDraft>> =
        c.programs.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val catalog: StateFlow<Map<String, ExerciseEntity>> =
        c.exercises.observeAll().map { list -> list.associateBy { it.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    fun deletePrograms(ids: Set<Long>, onDone: () -> Unit) {
        viewModelScope.launch {
            ids.forEach { c.programs.delete(it) }
            onDone()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProgramsScreen(onOpen: (Long) -> Unit, choosingExercise: Boolean = false,
    choosingProgram: Boolean = false, onBack: (() -> Unit)? = null, onCreate: () -> Unit = { onOpen(0L) }) {
    val vm = appViewModel { ProgramsViewModel(it) }
    val programs by vm.programs.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = selecting) { selecting = false; selectedIds = emptySet() }
    if (confirmBulkDelete) AlertDialog(
        onDismissRequest = { confirmBulkDelete = false },
        title = { Text(if (selectedIds.size == 1) "Удалить программу?" else "Удалить выбранные программы?") },
        text = { Column {
            programs.filter { it.id in selectedIds }.forEach { Text(it.name, color = Fc.Text) }
            Text("История выполненных тренировок сохранится.", color = Fc.Muted)
        } },
        dismissButton = { TextButton(onClick = { confirmBulkDelete = false }) { Text("Отмена") } },
        confirmButton = { TextButton(onClick = {
            confirmBulkDelete = false
            vm.deletePrograms(selectedIds) { selectedIds = emptySet(); selecting = false }
        }) { Text("Удалить", color = Fc.Pink) } }
    )

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onBack != null) BackRow("План тренировки", onBack)
                ScreenTitle(if (choosingExercise || choosingProgram) "Выберите программу" else "Программы")
                Text(
                    "${programs.size} ${plural(programs.size, "программа", "программы", "программ")} · создавайте сколько угодно",
                    style = MaterialTheme.typography.bodyMedium, color = Fc.Muted
                )
            }
        }
        item {
            if (selecting) Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selecting = false; selectedIds = emptySet() }) { Text("Отмена") }
                Text("Выбрано: ${selectedIds.size}", color = Fc.Text, modifier = Modifier.weight(1f))
                TextButton(onClick = { confirmBulkDelete = true }, enabled = selectedIds.isNotEmpty()) { Text("Удалить", color = Fc.Pink) }
            } else Column {
                OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("+ Создать программу") }
                Text("Удерживайте программу, чтобы выбрать для удаления.", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        items(programs, key = { it.id }) { p ->
            val fit = remember(p, catalog) { ProgramScorer.score(p, catalog) }
            FcCard(modifier = Modifier.combinedClickable(
                onClick = { if (selecting) selectedIds = if (p.id in selectedIds) selectedIds - p.id else selectedIds + p.id else onOpen(p.id) },
                onLongClickLabel = "Выбрать для удаления",
                onLongClick = { selecting = true; selectedIds = selectedIds + p.id }
            ), border = if (p.id in selectedIds || p.isActive) Fc.Lime.copy(alpha = 0.5f) else Fc.Line) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (selecting) Checkbox(checked = p.id in selectedIds, onCheckedChange = { checked ->
                        selectedIds = if (checked) selectedIds + p.id else selectedIds - p.id
                    })
                    ProgramCover(p.icon, Color(p.colorArgb), 60.dp, photo = p.coverPhoto)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium, color = Fc.Text)
                        Text(
                            "${p.goal.title} · ${p.days.size} ${plural(p.days.size, "день", "дня", "дней")} · ${p.exerciseCount} упр.",
                            style = MaterialTheme.typography.bodySmall, color = Fc.Muted
                        )
                        if (p.isActive) Text("АКТИВНАЯ", style = MaterialTheme.typography.labelSmall, color = Fc.Lime)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${fit.percent}%", style = MaterialTheme.typography.headlineSmall, color = Fc.fit(fit.percent / 100f))
                        Text("к цели", style = MaterialTheme.typography.labelSmall, color = Fc.Muted)
                    }
                }
            }
        }
    }
}

// ---------- Редактор программы ----------

class ProgramEditorViewModel(private val c: AppContainer, private val programId: Long, private val addExercise: String = "") : ViewModel() {
    private val _draft = MutableStateFlow<ProgramDraft?>(null)
    val draft: StateFlow<ProgramDraft?> = _draft.asStateFlow()
    val catalog: StateFlow<Map<String, ExerciseEntity>> =
        c.exercises.observeAll().map { list -> list.associateBy { it.id } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        viewModelScope.launch {
            val loaded = if (programId == 0L) ProgramDraft() else c.programs.get(programId) ?: ProgramDraft()
            _draft.value = if (addExercise.isNotBlank() && loaded.days.firstOrNull()?.items?.none { it.exerciseId == addExercise } != false)
                loaded.copy(days = (loaded.days.ifEmpty { listOf(DayDraft()) }).mapIndexed { index, day ->
                    if (index == 0) day.copy(items = day.items + ItemDraft(addExercise, 3, loaded.goal.defaultReps, loaded.goal.defaultRest)) else day
                }) else loaded
        }
    }

    fun update(fn: (ProgramDraft) -> ProgramDraft) = _draft.update { it?.let(fn) }

    fun updateItem(day: Int, index: Int, fn: (ItemDraft) -> ItemDraft) = update { d ->
        d.copy(days = d.days.mapIndexed { i, dd ->
            if (i != day) dd else dd.copy(items = dd.items.mapIndexed { j, it -> if (j == index) fn(it) else it })
        })
    }

    fun removeItem(day: Int, index: Int) = update { d ->
        d.copy(days = d.days.mapIndexed { i, dd -> if (i != day) dd else dd.copy(items = dd.items.filterIndexed { j, _ -> j != index }) })
    }

    fun addExercise(day: Int, exerciseId: String) = update { d ->
        val item = ItemDraft(exerciseId, 3, d.goal.defaultReps, d.goal.defaultRest)
        d.copy(days = d.days.mapIndexed { i, dd -> if (i != day) dd else dd.copy(items = dd.items + item) })
    }

    fun addDay() = update { it.copy(days = it.days + DayDraft()) }

    fun save(onDone: () -> Unit) {
        val d = _draft.value ?: return
        viewModelScope.launch {
            val id = c.programs.save(d)
            if (d.isActive) c.programs.setActive(id)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val d = _draft.value ?: return
        viewModelScope.launch {
            if (d.id != 0L) c.programs.delete(d.id)
            onDone()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgramEditorScreen(programId: Long, onClose: () -> Unit, addExercise: String = "") {
    val vm = appViewModel(key = "program-$programId-$addExercise") { ProgramEditorViewModel(it, programId, addExercise) }
    val draftOrNull by vm.draft.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val d = draftOrNull ?: return
    var dayIndex by rememberSaveable { mutableStateOf(0) }
    val day = dayIndex.coerceIn(0, d.days.lastIndex)
    var picking by rememberSaveable { mutableStateOf(false) }
    val fit = remember(d, catalog) { ProgramScorer.score(d, catalog) }
    val listState = rememberLazyListState()
    var photoBusy by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showTrainingSettings by rememberSaveable { mutableStateOf(false) }
    var showAnalysis by rememberSaveable { mutableStateOf(false) }
    if (confirmDelete) ProgramDeleteDialog(d.name, { confirmDelete = false }) {
        confirmDelete = false
        vm.delete(onClose)
    }

    if (picking) {
        BackHandler { picking = false }
        ExercisePicker(
            catalog = catalog.values.sortedBy { it.name },
            already = d.days[day].items.map { it.exerciseId }.toSet(),
            subtitle = "в день ${'A' + day} программы «${d.name}»",
            onBack = { picking = false },
            onPick = { vm.addExercise(day, it); picking = false }
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BackRow("Программы", onClose) }
                if (d.isActive) Pill("АКТИВНАЯ", Fc.LimeSoft, Fc.Lime)
                else TextButton(onClick = { vm.update { it.copy(isActive = true) } }) { Text("Сделать активной", color = Fc.Text) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProgramCover(d.icon, Color(d.colorArgb), 40.dp, photo = d.coverPhoto)
                OutlinedTextField(
                    value = d.name, onValueChange = { v -> vm.update { it.copy(name = v) } },
                    label = { Text("Название программы") }, singleLine = true, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Fc.Surface, unfocusedContainerColor = Fc.Surface,
                        focusedBorderColor = Fc.Lime, unfocusedBorderColor = Fc.Line,
                        focusedTextColor = Fc.Text, unfocusedTextColor = Fc.Text, cursorColor = Fc.Lime,
                        focusedLabelColor = Fc.Lime, unfocusedLabelColor = Fc.Muted
                    )
                )
            }
        }
        item { Text("${d.goal.title} · ${d.freqPerWeek} дня в неделю", color = Fc.Muted, style = MaterialTheme.typography.bodyMedium) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Дни программы", style = MaterialTheme.typography.titleSmall, color = Fc.Text)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.days.forEachIndexed { i, dd ->
                        val on = i == day
                        Text(
                            "День ${'A' + i} · ${dd.items.size}", style = MaterialTheme.typography.labelLarge,
                            color = if (on) Fc.Lime else Fc.Text,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Fc.LimeSoft else Fc.Surface)
                                .semantics { selected = on }
                                .clickable(role = Role.Tab) { dayIndex = i }
                                .padding(horizontal = 16.dp, vertical = 13.dp)
                        )
                    }
                    Text(
                        "+ День", style = MaterialTheme.typography.labelLarge, color = Fc.Muted,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).border(1.5.dp, Color(0xFF4A4A55), RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { vm.addDay(); dayIndex = d.days.size }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }
        }
        itemsIndexed(d.days[day].items) { index, item ->
            val e = catalog[item.exerciseId]
            ItemCard(
                name = e?.name ?: item.exerciseId,
                meta = e?.let { "${it.group.title} · ${it.type.title} · ${it.equipment}" } ?: "",
                item = item, goal = d.goal, exercise = e,
                onChange = { fn -> vm.updateItem(day, index, fn) },
                onRemove = { vm.removeItem(day, index) }
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (d.days[day].items.isEmpty()) Text("В этом дне пока нет упражнений.", style = MaterialTheme.typography.bodyMedium, color = Fc.Muted)
                PrimaryButton("+  Добавить упражнение", { picking = true })
            }
        }
        item { TextButton(onClick = { showTrainingSettings = !showTrainingSettings }, modifier = Modifier.fillMaxWidth()) { Text("Цель и расписание ${if (showTrainingSettings) "−" else "+"}", color = Fc.Text) } }
        if (showTrainingSettings) {
        item {
            Section("Цель тренировок") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Goal.entries.forEach { g ->
                        val on = d.goal == g
                        Text(
                            g.title, style = MaterialTheme.typography.labelLarge, color = if (on) Fc.Bg else Fc.Text,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) Fc.Text else Fc.Surface)
                                .border(1.dp, if (on) Fc.Text else Fc.Line, RoundedCornerShape(20.dp))
                                .semantics { selected = on }
                                .clickable(role = Role.RadioButton) { vm.update { it.copy(goal = g) } }
                                .padding(horizontal = 14.dp, vertical = 11.dp)
                        )
                    }
                }
            }
        }
        item {
            FcCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Тренировок в неделю", style = MaterialTheme.typography.titleSmall, color = Fc.Text)
                        Text("для цели «${d.goal.title}»: ${d.goal.freqPerWeek.first}–${d.goal.freqPerWeek.last}", style = MaterialTheme.typography.bodySmall, color = Fc.Muted)
                    }
                    Stepper(d.freqPerWeek, "тренировок в неделю", { vm.update { it.copy(freqPerWeek = (it.freqPerWeek - 1).coerceAtLeast(1)) } }, { vm.update { it.copy(freqPerWeek = (it.freqPerWeek + 1).coerceAtMost(7)) } })
                }
            }
        }
        }
        item { TextButton(onClick = { showAppearance = !showAppearance }, modifier = Modifier.fillMaxWidth()) { Text("Оформление программы ${if (showAppearance) "−" else "+"}", color = Fc.Text) } }
        if (showAppearance) {
        item {
            Section("Цвет") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PROGRAM_COLORS.forEach { (argb, title) ->
                        val on = d.colorArgb == argb
                        Box(
                            Modifier.size(44.dp)
                                .then(if (on) Modifier.border(2.dp, Fc.Text, CircleShape).padding(4.dp) else Modifier)
                                .clip(CircleShape).background(Color(argb))
                                .semantics { contentDescription = "Цвет: $title"; selected = on }
                                .clickable(role = Role.RadioButton) { vm.update { it.copy(colorArgb = argb) } }
                        )
                    }
                }
            }
        }
        item {
            Section("Обложка") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProgramIcons.all.forEach { icon ->
                        val on = d.icon == icon && d.coverPhoto == null
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(Fc.Surface)
                                .border(1.5.dp, if (on) Color(d.colorArgb) else Fc.Line, RoundedCornerShape(12.dp))
                                .semantics { contentDescription = "Обложка: ${IconPaths.titles[icon]}"; selected = on }
                                .clickable(role = Role.RadioButton) { vm.update { it.copy(icon = icon, coverPhoto = null) } },
                            contentAlignment = Alignment.Center
                        ) { StrokeIcon(icon, if (on) Color(d.colorArgb) else Fc.Muted, Modifier.size(22.dp), strokeWidth = 2f) }
                    }
                    ProgramPhotoButton(d.coverPhoto, { photo -> vm.update { it.copy(coverPhoto = photo) } }, Modifier.weight(1f), onBusy = { photoBusy = it })
                }
            }
        }
        }
        item { TextButton(onClick = { showAnalysis = !showAnalysis }, modifier = Modifier.fillMaxWidth()) { Text("Разбор программы ${if (showAnalysis) "−" else "+"}", color = Fc.Text) } }
        if (showAnalysis) item { FitDetails(fit, d.goal) }
        item {
            if (photoBusy) Text("Сохраняем фото…", color = Fc.Muted)
            else OutlinedButton(onClick = { vm.save(onClose) }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить программу") }
        }
        if (d.id != 0L) item {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Удалить программу", color = Fc.Pink, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}


@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Fc.Text)
        content()
    }
}

@Composable
private fun Stepper(value: Int, what: String, onDec: () -> Unit, onInc: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDec, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Fc.Surface2)) {
            Text("−", fontSize = 20.sp, color = Fc.Text, modifier = Modifier.semantics { contentDescription = "Меньше: $what" })
        }
        Text("$value", style = MaterialTheme.typography.titleMedium, color = Fc.Text, modifier = Modifier.width(34.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = onInc, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Fc.Surface2)) {
            Text("+", fontSize = 20.sp, color = Fc.Text, modifier = Modifier.semantics { contentDescription = "Больше: $what" })
        }
    }
}

@Composable
private fun FitDetails(fit: FitResult, goal: Goal) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("РАЗБОР ПРОГРАММЫ", style = MaterialTheme.typography.headlineSmall, color = Fc.Text, modifier = Modifier.weight(1f))
            Text("${fit.percent}%", style = MaterialTheme.typography.headlineSmall, color = Fc.fit(fit.percent / 100f))
        }
        if (fit.criteria.isNotEmpty()) FcCard {
            fit.criteria.forEachIndexed { i, c ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                Row {
                    Text(c.title, style = MaterialTheme.typography.labelLarge, color = Fc.Text, modifier = Modifier.weight(1f))
                    Text("${(c.value * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = Fc.fit(c.value))
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Fc.Line)) {
                    Box(Modifier.fillMaxWidth(c.value.coerceIn(0.03f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Fc.fit(c.value)))
                }
            }
        }
        Text("Рекомендации", style = MaterialTheme.typography.titleSmall, color = Fc.Text)
        fit.recommendations.forEach { r ->
            FcCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.width(6.dp).height(44.dp).clip(RoundedCornerShape(3.dp)).background(Fc.fit(r.severity)))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(r.title, style = MaterialTheme.typography.titleSmall, color = Fc.Text)
                        Text(r.text, style = MaterialTheme.typography.bodySmall, color = Fc.Muted)
                    }
                }
            }
        }
        Text(
            "Оценка основана на общепринятых рекомендациях по силовому тренингу (диапазоны повторений, отдых, объём, частота) и не заменяет работу с тренером.",
            style = MaterialTheme.typography.labelSmall, color = Fc.Muted2
        )
        FcCard(modifier = Modifier.testTag("program-fit-conclusion")) {
            Text("Итог для цели «${goal.title}»", style = MaterialTheme.typography.titleSmall, color = Fc.Text)
            Text(fit.verdict, style = MaterialTheme.typography.titleMedium, color = Fc.fit(fit.percent / 100f))
            Text("${fit.percent}%", color = Fc.Muted)
        }
    }
}

@Composable
internal fun ItemCard(
    name: String, meta: String, item: ItemDraft, goal: Goal,
    onChange: ((ItemDraft) -> ItemDraft) -> Unit, onRemove: () -> Unit, exercise: ExerciseEntity? = null
) {
    val repsOk = item.reps in goal.idealReps || item.reps in goal.okReps
    val restOk = item.restSec in goal.restOk
    FcCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            exercise?.let { ExerciseThumbnail(it, Modifier.width(88.dp).height(70.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = Fc.Text)
                Text(meta, style = MaterialTheme.typography.bodySmall, color = Fc.Muted)
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Убрать $name", tint = Fc.Muted2)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1.2f)) {
                Text("Подходы", style = MaterialTheme.typography.labelSmall, color = Fc.Muted)
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.height(44.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Fc.Bg).border(1.dp, Fc.Line, RoundedCornerShape(10.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onChange { it.copy(sets = (it.sets - 1).coerceAtLeast(1)) } }, modifier = Modifier.size(44.dp)) {
                        Text("−", fontSize = 18.sp, color = Fc.Text, modifier = Modifier.semantics { contentDescription = "Меньше подходов" })
                    }
                    Text("${item.sets}", style = MaterialTheme.typography.titleMedium, color = Fc.Text, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { onChange { it.copy(sets = (it.sets + 1).coerceAtMost(10)) } }, modifier = Modifier.size(44.dp)) {
                        Text("+", fontSize = 18.sp, color = Fc.Text, modifier = Modifier.semantics { contentDescription = "Больше подходов" })
                    }
                }
            }
            Dropdown(
                title = "Повторы", value = item.reps.label, ok = repsOk, modifier = Modifier.weight(1f),
                options = RepRange.entries.map { it.label to { onChange { x -> x.copy(reps = it) } } }
            )
            Dropdown(
                title = "Отдых", value = restLabel(item.restSec), ok = restOk, modifier = Modifier.weight(1f),
                options = REST_OPTIONS.map { sec -> restLabel(sec) to { onChange { x -> x.copy(restSec = sec) } } }
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = item.comment,
            onValueChange = { text -> onChange { it.copy(comment = text.take(2000)) } },
            label = { Text("Комментарий") }, placeholder = { Text("Хват, техника, рабочий вес…") },
            minLines = 2, maxLines = 4,
            modifier = Modifier.fillMaxWidth().testTag("exercise-comment-${item.exerciseId}"))
    }
}

@Composable
private fun Dropdown(title: String, value: String, ok: Boolean, options: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = Fc.Muted)
        Spacer(Modifier.height(4.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(Fc.Bg)
                    .border(1.5.dp, if (ok) Fc.Line else Fc.Pink, RoundedCornerShape(10.dp))
                    .clickable(role = Role.DropdownList) { open = true }
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(value, style = MaterialTheme.typography.labelLarge, color = Fc.Text, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Fc.Muted)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (label, action) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { action(); open = false })
                }
            }
        }
    }
}




@Composable
internal fun ProgramDeleteDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить программу?") },
        text = { Text("Программа «$name» будет удалена. Это действие нельзя отменить.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Удалить", color = Fc.Pink) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

