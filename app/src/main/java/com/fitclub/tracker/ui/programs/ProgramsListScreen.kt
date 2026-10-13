package com.fitclub.tracker.ui.programs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitclub.tracker.AppContainer
import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.repo.ProgramDraft
import com.fitclub.tracker.domain.ProgramScorer
import com.fitclub.tracker.ui.components.BackRow
import com.fitclub.tracker.ui.components.LineIcon
import com.fitclub.tracker.ui.components.LineIcons
import com.fitclub.tracker.ui.components.ProgramCover
import com.fitclub.tracker.ui.components.ScreenTitle
import com.fitclub.tracker.ui.navigation.appViewModel
import com.fitclub.tracker.ui.theme.Condensed
import com.fitclub.tracker.ui.theme.Fc
import com.fitclub.tracker.ui.theme.NumberStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TRASH = "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"
private const val ALERT = "M12 8v5M12 16.5v.5"
internal const val UNDO_SECONDS = 5

internal fun pluralRu(n: Int, one: String, few: String, many: String): String {
    val t = n % 100; val u = n % 10
    return when { t in 11..19 -> many; u == 1 -> one; u in 2..4 -> few; else -> many }
}

private fun programsWord(n: Int) = pluralRu(n, "программа", "программы", "программ")

/** Programs removed by the last delete, kept in memory so the user can put them back. */
data class DeletedPrograms(val programs: List<ProgramDraft>, val token: Long)

// ---------- Список программ ----------

class ProgramsViewModel(private val c: AppContainer) : ViewModel() {
    val programs: StateFlow<List<ProgramDraft>> =
        c.programs.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val catalog: StateFlow<Map<String, ExerciseEntity>> =
        c.exercises.observeAll().map { list -> list.associateBy { it.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    /** Number of workouts with at least one done set, per program. */
    val workoutCounts: StateFlow<Map<Long, Int>> =
        c.fitness.observeWorkouts().map { logs ->
            logs.filter { it.completedSets > 0 }.mapNotNull { it.session.programId }.groupingBy { it }.eachCount()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    var undo by mutableStateOf<DeletedPrograms?>(null)
        private set
    var undoSecondsLeft by mutableIntStateOf(0)
        private set
    private var undoTimer: Job? = null

    fun deletePrograms(ids: Set<Long>, onDone: () -> Unit = {}) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val snapshot = ids.mapNotNull { c.programs.get(it) }
            ids.forEach { c.programs.delete(it) }
            onDone()
            if (snapshot.isNotEmpty()) startUndo(DeletedPrograms(snapshot, System.nanoTime()))
        }
    }

    private fun startUndo(deleted: DeletedPrograms) {
        undoTimer?.cancel()
        undo = deleted
        undoSecondsLeft = UNDO_SECONDS
        undoTimer = viewModelScope.launch {
            for (s in UNDO_SECONDS downTo 1) { undoSecondsLeft = s; delay(1_000) }
            if (undo?.token == deleted.token) undo = null
        }
    }

    fun undoDelete() {
        val deleted = undo ?: return
        undoTimer?.cancel()
        undo = null
        viewModelScope.launch {
            // Restore the active one last so it stays active.
            deleted.programs.sortedBy { it.isActive }.forEach { c.programs.restore(it) }
        }
    }

    fun dismissUndo() { undoTimer?.cancel(); undo = null }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProgramsScreen(onOpen: (Long) -> Unit, choosingExercise: Boolean = false,
    choosingProgram: Boolean = false, onBack: (() -> Unit)? = null, onCreate: () -> Unit = { onOpen(0L) }) {
    val vm = appViewModel { ProgramsViewModel(it) }
    val programs by vm.programs.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val counts by vm.workoutCounts.collectAsStateWithLifecycle()
    val choosing = choosingExercise || choosingProgram
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var selecting by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Set<Long>?>(null) }
    val existing = programs.map { it.id }.toSet()
    LaunchedEffect(existing) { selectedIds = selectedIds intersect existing }
    fun exitSelection() { selecting = false; selectedIds = emptySet() }
    fun toggle(id: Long) { selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id }
    BackHandler(enabled = selecting) { exitSelection() }

    pendingDelete?.let { ids ->
        val chosen = programs.filter { it.id in ids }
        if (chosen.isNotEmpty()) DeleteProgramsSheet(chosen, counts,
            onDismiss = { pendingDelete = null },
            onConfirm = { pendingDelete = null; vm.deletePrograms(chosen.map { it.id }.toSet()) { exitSelection() } })
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("programs-list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = if (selecting || vm.undo != null) 120.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "header") {
                if (selecting) SelectionHeader(
                    count = selectedIds.size,
                    allSelected = programs.isNotEmpty() && selectedIds.size == programs.size,
                    onClose = { exitSelection() },
                    onToggleAll = { selectedIds = if (selectedIds.size == programs.size) emptySet() else existing }
                ) else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onBack != null) BackRow("План тренировки", onBack)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ScreenTitle(if (choosing) "Выберите программу" else "Программы")
                            Text("${programs.size} ${programsWord(programs.size)}", style = MaterialTheme.typography.bodyMedium, color = Fc.Muted)
                        }
                        Surface(onClick = onCreate, color = Fc.Lime, contentColor = Fc.OnAccent, shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.heightIn(min = 44.dp).testTag("program-create")) {
                            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                                Text("+ Новая", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Fc.OnAccent)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            items(programs, key = { it.id }) { p ->
                val fit = remember(p, catalog) { ProgramScorer.score(p, catalog) }
                val selected = p.id in selectedIds
                val row = @Composable {
                    ProgramRow(
                        p, fit.percent, selected, selecting,
                        onClick = { if (selecting) toggle(p.id) else onOpen(p.id) },
                        onLongClick = if (choosing) null else {
                            { vm.dismissUndo(); selecting = true; selectedIds = selectedIds + p.id }
                        },
                        onDelete = if (choosing || selecting) null else { { pendingDelete = setOf(p.id) } }
                    )
                }
                if (choosing || selecting) row() else SwipeToDelete(onDelete = { pendingDelete = setOf(p.id) }) { row() }
            }
            if (programs.isEmpty()) item(key = "empty") {
                Text("Пока нет программ. Создайте первую — это займёт пару минут.", color = Fc.Muted, style = MaterialTheme.typography.bodyMedium)
            } else if (!choosing && !selecting) item(key = "hint") { GestureHint() }
        }

        AnimatedVisibility(selecting, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            SelectionBar(selectedIds.size, onDelete = { if (selectedIds.isNotEmpty()) pendingDelete = selectedIds })
        }
        val undo = vm.undo
        AnimatedVisibility(undo != null && !selecting, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            if (undo != null) UndoBar(undo, vm.undoSecondsLeft, onUndo = vm::undoDelete)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val latest by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart) latest()
        false // the card snaps back; the confirmation sheet decides
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Fc.Danger).padding(end = 28.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LineIcon(TRASH, Color.White, Modifier.size(24.dp))
                    Text("Удалить", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProgramRow(
    p: ProgramDraft, score: Int, selected: Boolean, selecting: Boolean,
    onClick: () -> Unit, onLongClick: (() -> Unit)?, onDelete: (() -> Unit)?
) {
    val shape = RoundedCornerShape(22.dp)
    val border = when { selected -> Fc.Lime; p.isActive -> Fc.Lime.copy(alpha = 0.5f); else -> Fc.Line2 }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 84.dp).clip(shape)
            .background(if (selected) Fc.LimeSoft else Fc.Surface)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .semantics {
                if (selecting) this.selected = selected
                if (onDelete != null) customActions = listOf(CustomAccessibilityAction("Удалить программу") { onDelete(); true })
            }
            .combinedClickable(
                role = if (selecting) Role.Checkbox else Role.Button,
                onClick = onClick,
                onLongClickLabel = if (onLongClick != null) "Выбрать несколько" else null,
                onLongClick = onLongClick
            )
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(62.dp)) {
            ProgramCover(p.icon, Color(p.colorArgb), 56.dp, Modifier.align(Alignment.TopStart).alpha(if (selected) 0.45f else 1f), photo = p.coverPhoto)
            if (selecting) CheckBadge(selected, Modifier.align(Alignment.BottomEnd))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, color = Fc.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${p.goal.title} · ${p.days.size} ${pluralRu(p.days.size, "день", "дня", "дней")} · ${p.exerciseCount} упр.",
                style = MaterialTheme.typography.bodySmall, color = Fc.Muted
            )
            if (p.isActive) ActiveTag()
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Структура $score из 100" }) {
            Text("$score", style = NumberStyle.copy(fontSize = 20.sp), color = Fc.fit(score / 100f))
            Text("из 100", style = MaterialTheme.typography.labelSmall, color = Fc.Muted)
        }
    }
}

@Composable
private fun ActiveTag() {
    Text("Активная", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Fc.Lime,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Fc.LimeSoft).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
private fun CheckBadge(on: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(26.dp).clip(CircleShape)
            .background(if (on) Fc.Lime else Fc.Surface)
            .border(2.dp, if (on) Fc.Bg else Fc.Line, CircleShape),
        contentAlignment = Alignment.Center
    ) { if (on) LineIcon(LineIcons.CHECK, Fc.OnAccent, Modifier.size(16.dp), strokeWidth = 3.2f) }
}

@Composable
private fun SelectionHeader(count: Int, allSelected: Boolean, onClose: () -> Unit, onToggleAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(onClick = onClose, shape = CircleShape, color = Fc.Surface2, modifier = Modifier.size(44.dp)
            .semantics { contentDescription = "Выйти из выбора" }) {
            Box(contentAlignment = Alignment.Center) { LineIcon(LineIcons.CLOSE, Fc.Text, Modifier.size(18.dp), strokeWidth = 2.4f) }
        }
        Text("Выбрано $count", Modifier.weight(1f), fontFamily = Condensed, fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp, color = Fc.Text)
        Surface(onClick = onToggleAll, shape = RoundedCornerShape(22.dp), color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(1.dp, Fc.Line), modifier = Modifier.heightIn(min = 44.dp)) {
            Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(if (allSelected) "Снять все" else "Все", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Fc.Text)
            }
        }
    }
}

@Composable
private fun dockColor() = if (Fc.isLight) Color(0xFF101315) else Fc.Line2

@Composable
private fun DockBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp).fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(dockColor())
    ) { content() }
}

@Composable
private fun SelectionBar(count: Int, onDelete: () -> Unit) {
    DockBar {
        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (count == 0) "Ничего не выбрано" else "$count ${programsWord(count)}",
                    color = Color(0xFFF2F4EE), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("История тренировок сохранится", color = Color(0xFFA3AAA2), fontSize = 12.sp)
            }
            Surface(onClick = onDelete, enabled = count > 0, shape = RoundedCornerShape(16.dp),
                color = if (count > 0) Fc.Danger else Fc.Danger.copy(alpha = 0.4f), modifier = Modifier.heightIn(min = 48.dp).testTag("programs-delete")) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LineIcon(TRASH, Color.White, Modifier.size(18.dp), strokeWidth = 2.2f)
                    Text("Удалить", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun UndoBar(deleted: DeletedPrograms, secondsLeft: Int, onUndo: () -> Unit) {
    val ring = remember(deleted.token) { Animatable(1f) }
    LaunchedEffect(deleted.token, secondsLeft) {
        ring.animateTo(((secondsLeft - 1).coerceAtLeast(0)) / UNDO_SECONDS.toFloat(), tween(1_000, easing = LinearEasing))
    }
    val n = deleted.programs.size
    val title = if (n == 1) "«${deleted.programs.first().name}» удалена" else "$n ${programsWord(n)} удалены"
    val track = if (Fc.isLight) Color(0xFF2A3035) else Color(0xFF3A4248)
    val accent = if (Fc.isLight) Fc.OnAccent else Fc.Lime
    DockBar(Modifier.navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }.testTag("programs-undo"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(36.dp)) {
                    val stroke = 3.dp.toPx()
                    val inset = stroke / 2 + 1.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(accent, -90f, 360f * ring.value, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text("${secondsLeft.coerceAtLeast(1)}", style = NumberStyle.copy(fontSize = 15.sp), color = Color(0xFFF2F4EE))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, color = Color(0xFFF2F4EE), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("История тренировок на месте", color = Color(0xFFA3AAA2), fontSize = 12.sp)
            }
            Surface(onClick = onUndo, color = Color.Transparent, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = 44.dp).testTag("programs-undo-action")) {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text("Вернуть", color = accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun GestureHint() {
    val line = Fc.Line
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp)
            .drawDashedBorder(line)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LineIcon("M19 12H5M11 6l-6 6 6 6", Fc.Muted, Modifier.size(20.dp))
        Text("Смахните программу влево, чтобы удалить одну. Удерживайте, чтобы выбрать несколько.",
            color = Fc.Muted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

private fun Modifier.drawDashedBorder(color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    val r = 18.dp.toPx()
    drawRoundRect(color, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteProgramsSheet(programs: List<ProgramDraft>, workoutCounts: Map<Long, Int>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val n = programs.size
    val title = if (n == 1) "Удалить программу?" else "Удалить $n ${programsWord(n)}?"
    val action = if (n == 1) "Удалить программу" else "Удалить $n ${programsWord(n)}"
    val workouts = programs.sumOf { workoutCounts[it.id] ?: 0 }
    val active = programs.firstOrNull { it.isActive }
    val dangerSoft = Fc.Danger.copy(alpha = if (Fc.isLight) 0.10f else 0.16f)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Fc.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp).testTag("programs-delete-sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(dangerSoft), contentAlignment = Alignment.Center) {
                    LineIcon(TRASH, Fc.Danger, Modifier.size(26.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 28.sp, color = Fc.Text)
                    Text("Отменить можно в течение $UNDO_SECONDS секунд", fontSize = 13.sp, color = Fc.Muted)
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, Fc.Line2, RoundedCornerShape(20.dp))) {
                programs.take(6).forEachIndexed { i, p ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Fc.Line2))
                    val used = workoutCounts[p.id] ?: 0
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProgramCover(p.icon, Color(p.colorArgb), 40.dp, photo = p.coverPhoto)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Fc.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.days.size} ${pluralRu(p.days.size, "день", "дня", "дней")} · ${p.exerciseCount} упр. · " +
                                if (used == 0) "не использовалась" else "$used ${pluralRu(used, "тренировка", "тренировки", "тренировок")}",
                                fontSize = 12.sp, color = Fc.Muted)
                        }
                        if (p.isActive) ActiveTag()
                    }
                }
                if (programs.size > 6) Text("и ещё ${programs.size - 6}", Modifier.padding(14.dp), fontSize = 13.sp, color = Fc.Muted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Fact(if (workouts > 0) "История $workouts ${pluralRu(workouts, "тренировки", "тренировок", "тренировок")} и рекорды сохранятся"
                    else "История тренировок и рекорды сохранятся", ok = true)
                Fact("Тренировки в календаре останутся как личные планы", ok = true)
                if (active != null) Fact("«${active.name}» — активная. «Сегодня» перестанет предлагать её дни, пока вы не выберете другую программу", ok = false)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Surface(onClick = onConfirm, color = Fc.Danger, shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).testTag("programs-delete-confirm")) {
                    Box(contentAlignment = Alignment.Center) { Text(action, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
                Surface(onClick = onDismiss, color = Color.Transparent, shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Fc.Line),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("Отмена", color = Fc.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
                }
            }
        }
    }
}

@Composable
private fun Fact(text: String, ok: Boolean) {
    val bg = if (ok) Fc.LimeSoft else Fc.Danger.copy(alpha = if (Fc.isLight) 0.10f else 0.16f)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 1.dp).size(22.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            LineIcon(if (ok) LineIcons.CHECK else ALERT, if (ok) Fc.Lime else Fc.Danger, Modifier.size(14.dp), strokeWidth = 3f)
        }
        Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = if (ok) Fc.Muted else Fc.Text, modifier = Modifier.width(0.dp).weight(1f))
    }
}
