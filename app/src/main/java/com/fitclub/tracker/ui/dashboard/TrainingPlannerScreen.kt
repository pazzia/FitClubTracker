package com.fitclub.tracker.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitclub.tracker.AppContainer
import com.fitclub.tracker.data.model.Goal
import com.fitclub.tracker.data.model.RepRange
import com.fitclub.tracker.data.repo.*
import com.fitclub.tracker.ui.components.*
import com.fitclub.tracker.ui.navigation.appViewModel
import com.fitclub.tracker.ui.programs.ExercisePicker
import com.fitclub.tracker.ui.programs.ItemCard
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TrainingPlannerViewModel(private val c: AppContainer, epochDay: Long) : ViewModel() {
    val draft = MutableStateFlow<ScheduledDraft?>(null)
    val error = MutableStateFlow<String?>(null)
    val saving = MutableStateFlow(false)
    val programs = c.programs.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val catalog = c.exercises.observeAll().map { it.associateBy { e -> e.id } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val workouts = c.fitness.observeWorkouts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private var initial: ScheduledDraft? = null
    val dirty: Boolean get() = draft.value != initial

    init { viewModelScope.launch {
        runCatching { c.schedule.getForDate(epochDay) ?: ScheduledDraft(epochDay = epochDay) }
            .onSuccess { initial = it; draft.value = it }
            .onFailure { error.value = "Не удалось загрузить план: ${it.message}" }
    } }
    fun update(fn: (ScheduledDraft) -> ScheduledDraft) { draft.update { it?.let(fn) }; error.value = null }
    fun add(exerciseId: String) = update { it.copy(items = it.items + ItemDraft(exerciseId, 3, RepRange.R8_12, 90)) }
    fun save(onSaved: (Long) -> Unit) {
        val value = draft.value ?: return
        if (saving.value || !value.canSave) return
        saving.value = true
        viewModelScope.launch {
            runCatching { c.schedule.save(value) }.onSuccess { id ->
                initial = value.copy(id = id); draft.value = initial; onSaved(id)
            }.onFailure { error.value = "Не удалось сохранить: ${it.message}" }
            saving.value = false
        }
    }
    fun delete(onDeleted: () -> Unit) {
        val id = draft.value?.id ?: return
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            runCatching { c.schedule.delete(id) }.onSuccess { onDeleted() }
                .onFailure { error.value = "Не удалось удалить: ${it.message}" }
            saving.value = false
        }
    }
}

fun parseScheduleTime(value: String): Int? {
    if (!Regex("\\d{1,2}:\\d{2}").matches(value)) return null
    val parts = value.split(':').map { it.toInt() }
    return if (parts[0] in 0..23 && parts[1] in 0..59) parts[0] * 60 + parts[1] else null
}

@Composable
fun TrainingPlannerScreen(epochDay: Long, onClose: () -> Unit, onStart: (Long) -> Unit) {
    val vm = appViewModel(key = "plan-$epochDay") { TrainingPlannerViewModel(it, epochDay) }
    val value by vm.draft.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val programs by vm.programs.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmBack by rememberSaveable { mutableStateOf(false) }
    val close = { if (vm.dirty) confirmBack = true else onClose() }
    BackHandler { if (picker) picker = false else close() }
    val draft = value
    if (draft == null) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BackRow("Календарь", onClose)
            if (error == null) CircularProgressIndicator(color = Fc.Lime) else Text(error.orEmpty(), color = Fc.Pink)
        }
        return
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Удалить план?") },
        text = { Text("История выполненных тренировок сохранится.") },
        confirmButton = { TextButton(onClick = { vm.delete(onClose) }, enabled = !saving) { Text("Удалить", color = Fc.Pink) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } })
    if (confirmBack) AlertDialog(onDismissRequest = { confirmBack = false }, title = { Text("Выйти без сохранения?") },
        confirmButton = { TextButton(onClick = onClose) { Text("Выйти") } },
        dismissButton = { TextButton(onClick = { confirmBack = false }) { Text("Продолжить") } })
    if (picker) {
        ExercisePicker(catalog.values.sortedBy { it.name }, draft.items.map { it.exerciseId }.toSet(), calendarDate(epochDay),
            onBack = { picker = false }, onPick = { vm.add(it); picker = false })
        return
    }
    var templateMenu by remember { mutableStateOf(false) }
    var time by rememberSaveable(draft.id) { mutableStateOf(scheduleTime(draft.startMinute)) }
    val minute = parseScheduleTime(time)
    val canSave = draft.canSave && minute != null && !saving
    val completed = workouts.any { it.session.scheduleId == draft.id && draft.id != 0L }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BackRow("Календарь", close) }
        item {
            Text(calendarDate(epochDay), color = Fc.Muted, style = MaterialTheme.typography.labelMedium)
            ScreenTitle(if (draft.id == 0L) "План тренировки" else "Тренировка")
            if (completed) Pill("Выполнено · результат в календаре", Fc.LimeSoft, Fc.Lime)
        }
        item {
            OutlinedTextField(draft.title, { text -> vm.update { it.copy(title = text) } }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(time, { text -> time = text; parseScheduleTime(text)?.let { m -> vm.update { it.copy(startMinute = m) } } },
                label = { Text("Время · ЧЧ:ММ") }, singleLine = true, isError = minute == null, modifier = Modifier.fillMaxWidth())
        }
        item {
            FcCard {
                Text("Взять день из программы", color = Fc.Text, style = MaterialTheme.typography.titleSmall)
                Box {
                    TextButton(onClick = { templateMenu = true }) { Text(programs.firstOrNull { it.id == draft.programId }?.name ?: "Выбрать программу ↓", color = Fc.Lime) }
                    DropdownMenu(templateMenu, onDismissRequest = { templateMenu = false }) {
                        programs.forEach { program -> DropdownMenuItem(text = { Text(program.name) }, onClick = {
                            vm.update { it.fromProgram(program, 0) }; templateMenu = false
                        }) }
                        if (programs.isEmpty()) DropdownMenuItem(text = { Text("Нет программ — добавьте упражнения ниже") }, onClick = { templateMenu = false })
                    }
                }
                val program = programs.firstOrNull { it.id == draft.programId }
                if (program != null && program.days.isNotEmpty()) {
                    Segmented(program.days.indices.toList(), draft.dayPosition ?: 0,
                        { day -> vm.update { it.fromProgram(program, day) } }, { "День ${'A' + it}" }, selectedBg = Fc.Lime)
                }
                Text("Упражнения копируются в план на эту дату. Ниже можно изменить их настройки.", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Text("${draft.items.size} упражнений · ${draft.items.sumOf { it.sets }} подходов", color = Fc.Muted) }
        itemsIndexed(draft.items, key = { index, item -> "${item.exerciseId}-$index" }) { index, item ->
            val exercise = catalog[item.exerciseId]
            ItemCard(exercise?.name ?: item.exerciseId, exercise?.equipment.orEmpty(), item, Goal.GENERAL,
                onChange = { fn -> vm.update { it.copy(items = it.items.mapIndexed { i, old -> if (i == index) fn(old) else old }) } },
                onRemove = { vm.update { it.copy(items = it.items.filterIndexed { i, _ -> i != index }) } })
        }
        item { PrimaryButton("+ Добавить упражнение", { picker = true }) }
        if (error != null) item { Text(error.orEmpty(), color = Fc.Pink) }
        item {
            Button(onClick = { vm.save { onClose() } }, enabled = canSave, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Fc.Lime)) {
                Text(if (saving) "Сохраняю…" else "Сохранить план", modifier = Modifier.padding(6.dp))
            }
            if (draft.items.isEmpty()) Text("Добавьте хотя бы одно упражнение", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
            if (!completed) OutlinedButton(onClick = { vm.save(onStart) }, enabled = canSave, modifier = Modifier.fillMaxWidth()) { Text("Сохранить и начать тренировку") }
            if (draft.id != 0L) TextButton(onClick = { confirmDelete = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Удалить план на этот день", color = Fc.Pink) }
        }
    }
}
