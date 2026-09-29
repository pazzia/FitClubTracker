package com.fitclub.tracker.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitclub.tracker.AppContainer
import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.repo.CompletedSet
import com.fitclub.tracker.data.repo.ProgramDraft
import com.fitclub.tracker.ui.components.*
import com.fitclub.tracker.ui.navigation.appViewModel
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

data class WorkoutSource(val program: ProgramDraft? = null, val catalog: Map<String, ExerciseEntity> = emptyMap())
data class SetEntry(var weight: String = "", var reps: String = "", var done: Boolean = false)

class WorkoutViewModel(private val c: AppContainer) : ViewModel() {
    val source = combine(c.programs.observeActive(), c.exercises.observeAll()) { p, e -> WorkoutSource(p, e.associateBy { it.id }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutSource())
    fun finish(programId: Long?, day: Int, startedAt: Long, sets: List<CompletedSet>, done: () -> Unit) = viewModelScope.launch {
        c.fitness.finishWorkout(programId, day, startedAt, sets); done()
    }
}

@Composable
fun WorkoutScreen(dayIndex: Int, onClose: () -> Unit, onTechnique: (String) -> Unit) {
    val vm = appViewModel { WorkoutViewModel(it) }
    val source by vm.source.collectAsStateWithLifecycle()
    val p = source.program
    val day = p?.days?.getOrNull(dayIndex.coerceAtLeast(0))
    val startedAt = remember { System.currentTimeMillis() }
    var elapsed by remember { mutableLongStateOf(0L) }
    var exerciseIndex by remember { mutableIntStateOf(0) }
    var rest by remember { mutableIntStateOf(0) }
    var confirmClose by remember { mutableStateOf(false) }
    val logs = remember { mutableStateMapOf<String, SnapshotStateList<SetEntry>>() }

    LaunchedEffect(Unit) { while (true) { elapsed = (System.currentTimeMillis() - startedAt) / 1000; delay(1000) } }
    LaunchedEffect(rest) { if (rest > 0) { delay(1000); rest -= 1 } }
    BackHandler { confirmClose = true }

    if (confirmClose) AlertDialog(
        onDismissRequest = { confirmClose = false },
        title = { Text("Прервать тренировку?") },
        text = { Text("Незаписанные подходы будут потеряны.") },
        confirmButton = { TextButton(onClick = onClose) { Text("Выйти", color = Fc.Pink) } },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Продолжить") } }
    )

    if (p == null || day == null || day.items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("В этом дне пока нет упражнений", color = Fc.Text, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClose) { Text("Вернуться к программе", color = Fc.Lime) }
            }
        }
        return
    }
    val item = day.items[exerciseIndex.coerceIn(0, day.items.lastIndex)]
    val exercise = source.catalog[item.exerciseId]
    val rows = logs.getOrPut(item.exerciseId) {
        mutableStateListOf<SetEntry>().apply { repeat(item.sets) { add(SetEntry(weight = suggestedWeight(item.exerciseId))) } }
    }
    val allDone = day.items.flatMap { i -> logs[i.exerciseId].orEmpty() }.filter { it.done }

    Column(Modifier.fillMaxSize().background(Fc.Bg)) {
        Row(Modifier.fillMaxWidth().background(Fc.Nav).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("${p.name} · день ${'A' + dayIndex}", style = MaterialTheme.typography.titleSmall, color = Fc.Text); Text(time(elapsed), color = Fc.Lime, style = MaterialTheme.typography.labelMedium) }
            TextButton(onClick = {
                val completed = logs.flatMap { (id, list) -> list.mapIndexedNotNull { idx, r -> if (r.done) CompletedSet(id, idx, r.weight.replace(',', '.').toFloatOrNull() ?: 0f, r.reps.toIntOrNull() ?: 0) else null } }
                vm.finish(p.id, dayIndex, startedAt, completed, onClose)
            }, enabled = allDone.isNotEmpty()) { Text("Завершить", color = if (allDone.isNotEmpty()) Fc.Lime else Fc.Muted) }
        }
        LinearProgressIndicator(progress = { (exerciseIndex + 1f) / day.items.size }, modifier = Modifier.fillMaxWidth(), color = Fc.Lime, trackColor = Fc.Surface2)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("УПРАЖНЕНИЕ ${exerciseIndex + 1} ИЗ ${day.items.size}", style = MaterialTheme.typography.labelMedium, color = Fc.Muted); Text(exercise?.name ?: item.exerciseId, style = MaterialTheme.typography.displaySmall, color = Fc.Text) }
            item { TextButton(onClick = { onTechnique(item.exerciseId) }, contentPadding = PaddingValues(0.dp)) { Text("Техника выполнения →", color = Fc.Lime) } }
            item {
                FcCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("№", color = Fc.Muted, textAlign = TextAlign.Center, modifier = Modifier.width(34.dp)); Text("кг", color = Fc.Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)); Text("повт.", color = Fc.Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)); Spacer(Modifier.width(46.dp))
                    }
                    rows.forEachIndexed { index, row ->
                        SetRow(index, row, onDone = { checked ->
                            rows[index] = row.copy(done = checked)
                            if (checked) rest = item.restSec
                        }, onRemove = { if (rows.size > 1) rows.removeAt(index) }) { w, r -> rows[index] = row.copy(weight = w, reps = r) }
                    }
                    TextButton(onClick = { rows.add(SetEntry(weight = rows.lastOrNull()?.weight ?: suggestedWeight(item.exerciseId))) }, modifier = Modifier.fillMaxWidth()) { Text("+ Добавить подход", color = Fc.Lime) }
                }
            }
            if (rest > 0) item {
                FcCard(border = Fc.Lime.copy(.45f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(58.dp).background(Fc.LimeSoft, CircleShape), contentAlignment = Alignment.Center) { Text(time(rest.toLong()), color = Fc.Lime, fontWeight = FontWeight.ExtraBold) }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) { Text("Отдых", style = MaterialTheme.typography.titleMedium, color = Fc.Text); Text("готовы к следующему подходу", color = Fc.Muted) }
                        TextButton(onClick = { rest = 0 }) { Text("Пропустить", color = Fc.Lime) }
                    }
                }
            }
            item {
                val next = day.items.getOrNull(exerciseIndex + 1)
                if (next != null) FcCard {
                    Text("ДАЛЕЕ", style = MaterialTheme.typography.labelMedium, color = Fc.Muted)
                    Text(source.catalog[next.exerciseId]?.name ?: next.exerciseId, style = MaterialTheme.typography.titleMedium, color = Fc.Text)
                    Text("${next.sets} × ${next.reps.label}", color = Fc.Muted)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { if (exerciseIndex > 0) exerciseIndex-- }, enabled = exerciseIndex > 0, modifier = Modifier.weight(1f)) { Text("Назад") }
            Button(onClick = { if (exerciseIndex < day.items.lastIndex) exerciseIndex++ else {
                val completed = logs.flatMap { (id, list) -> list.mapIndexedNotNull { idx, r -> if (r.done) CompletedSet(id, idx, r.weight.replace(',', '.').toFloatOrNull() ?: 0f, r.reps.toIntOrNull() ?: 0) else null } }
                vm.finish(p.id, dayIndex, startedAt, completed, onClose)
            } }, colors = ButtonDefaults.buttonColors(containerColor = Fc.Lime), modifier = Modifier.weight(1f)) { Text(if (exerciseIndex == day.items.lastIndex) "Завершить" else "Далее", color = Fc.Bg) }
        }
    }
}

@Composable private fun SetRow(index: Int, row: SetEntry, onDone: (Boolean) -> Unit, onRemove: () -> Unit, onEdit: (String, String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${index + 1}", color = if (row.done) Fc.Lime else Fc.Text, textAlign = TextAlign.Center, modifier = Modifier.width(34.dp))
        OutlinedTextField(row.weight, { onEdit(it, row.reps) }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = fieldColors())
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(row.reps, { onEdit(row.weight, it.filter(Char::isDigit)) }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = fieldColors())
        IconButton(onClick = { onDone(!row.done) }) { Icon(Icons.Filled.Check, "Подход выполнен", tint = if (row.done) Fc.Bg else Fc.Muted, modifier = Modifier.background(if (row.done) Fc.Lime else Fc.Surface2, CircleShape).padding(5.dp)) }
        if (!row.done && index > 2) IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.Delete, "Удалить подход", tint = Fc.Muted) }
    }
}

@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedTextColor = Fc.Text, unfocusedTextColor = Fc.Text, focusedBorderColor = Fc.Lime, unfocusedBorderColor = Fc.Line, cursorColor = Fc.Lime)
private fun time(sec: Long) = "%02d:%02d".format(Locale.US, sec / 60, sec % 60)
private fun suggestedWeight(id: String) = when (id) { "squat" -> "70"; "bench" -> "57,5"; "bbrow" -> "50"; "rdl" -> "65"; "deadlift" -> "90"; else -> "20" }
