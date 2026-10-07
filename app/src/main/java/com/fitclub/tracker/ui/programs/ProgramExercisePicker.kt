package com.fitclub.tracker.ui.programs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.model.MuscleGroup
import com.fitclub.tracker.data.model.Sex
import com.fitclub.tracker.ui.components.*
import com.fitclub.tracker.ui.library.ExerciseSearchIndex
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Selection remains local until one batch is confirmed by the program editor. */
@Composable
internal fun ProgramExercisePicker(catalog: List<ExerciseEntity>, already: Set<String>, subtitle: String,
    onBack: () -> Unit, onPick: (List<String>) -> Unit, sex: Sex = Sex.MALE) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedGroup by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    var tree by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var openGroups by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var collapsedSearch by rememberSaveable(query) { mutableStateOf(arrayListOf<String>()) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val index = remember(catalog) { ExerciseSearchIndex(catalog) }
    var matches by remember(index) { mutableStateOf(catalog) }
    var appliedQuery by remember(index) { mutableStateOf("") }
    LaunchedEffect(query, index) {
        delay(100)
        val found = withContext(Dispatchers.Default) { index.search(query) }
        matches = found
        appliedQuery = query
    }
    val ready = appliedQuery == query
    val shown = remember(matches, tree, reviewing, query, selectedGroup) {
        if (!tree && !reviewing && query.isBlank() && selectedGroup != null)
            matches.filter { it.group == selectedGroup } else matches
    }
    val available = remember(shown, already) { shown.filter { it.id !in already }.map { it.id } }
    val selectedSet = selected.toSet()
    val validSelected = selected.filter { id -> id !in already && catalog.any { it.id == id } }
    val hidden = validSelected.count { id -> matches.none { it.id == id } }
    val groups = remember(matches) { matches.groupBy { it.group } }
    val listState = rememberLazyListState()
    LaunchedEffect(appliedQuery, tree, reviewing, selectedGroup) { listState.scrollToItem(0) }
    fun close() {
        when {
            reviewing -> reviewing = false
            !tree && selectedGroup != null -> selectedGroup = null
            validSelected.isNotEmpty() -> discard = true
            else -> onBack()
        }
    }
    fun toggle(ids: List<String>) {
        selected = ArrayList(if (ids.all { it in selectedSet }) selected.filter { it !in ids.toSet() }
            else (selected + ids).distinct())
    }
    BackHandler { close() }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Отменить выбор упражнений?") },
        text = { Text("Выбрано: ${validSelected.size}. Упражнения ещё не добавлены в программу.") },
        confirmButton = { TextButton(onClick = onBack) { Text("Отменить выбор") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Продолжить выбор") } })

    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
    val compact = maxHeight < 550.dp
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val backLabel = if (reviewing) "К выбору упражнений" else if (!tree && selectedGroup != null) "Группы мышц" else "К программе"
            val title = if (reviewing) "Выбранные упражнения" else "Добавить упражнения"
            if (compact) Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, backLabel) }
                Text(title, style = MaterialTheme.typography.titleLarge, color = Fc.Text, modifier = Modifier.weight(1f))
            } else {
                BackRow(backLabel) { close() }
                ScreenTitle(title)
                Text(subtitle, color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (!reviewing) {
                OutlinedTextField(query, { query = it.take(120) }, modifier = Modifier.fillMaxWidth().testTag("exercise-search"),
                    singleLine = true, label = { Text("Поиск упражнений") }, placeholder = { Text("Название, мышцы, оборудование") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Очистить поиск") }
                    })
                OutlinedButton(onClick = { tree = !tree }, modifier = Modifier.fillMaxWidth().testTag("picker-view")) {
                    Text(if (tree) "С иконками" else "Весь справочник")
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (ready) "Найдено: ${shown.size}" else "Поиск…", modifier = Modifier.weight(1f), color = Fc.Muted)
                    TextButton(onClick = { toggle(available) }, enabled = ready && available.isNotEmpty(), modifier = Modifier.testTag("picker-select-all")) {
                        Text(if (available.isNotEmpty() && available.all { it in selectedSet }) "Снять все" else "Выбрать все")
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("picker-list"), state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (reviewing) {
                items(validSelected.mapNotNull { id -> catalog.firstOrNull { it.id == id } }, key = { it.id }) { e ->
                    PickerExerciseRow(e, true, false) { toggle(listOf(e.id)) }
                }
            } else if (!tree && query.isBlank() && selectedGroup == null) {
                item(key = "categories-title") {
                    Text("Группы мышц", color = Fc.Text, style = MaterialTheme.typography.titleMedium)
                }
                val counts = catalog.groupingBy { it.group }.eachCount()
                items(AnatomyGroupOrder.filter { counts.getOrDefault(it, 0) > 0 }.chunked(2),
                    key = { "categories-${it.first().name}" }) { pair ->
                    MuscleCategoryRow(pair, sex, counts) { selectedGroup = it }
                }
            } else if (!tree) {
                if (query.isBlank()) selectedGroup?.let { group ->
                    item(key = "category-image") {
                        GroupImage(sex, group, Modifier.fillMaxWidth().height(if (compact) 96.dp else 180.dp))
                        Text(group.title, color = Fc.Text, style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(shown, key = { it.id }) { e ->
                    PickerExerciseRow(e, e.id in selectedSet, e.id in already) { toggle(listOf(e.id)) }
                }
            } else {
                MuscleGroup.entries.forEach { group ->
                    val exercises = groups[group].orEmpty()
                    if (exercises.isNotEmpty()) {
                        val expanded = if (query.isBlank()) group.name in openGroups else group.name !in collapsedSearch
                        val eligible = exercises.filter { it.id !in already }.map { it.id }
                        item(key = "group-${group.name}") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TriStateCheckbox(state = when {
                                    eligible.isNotEmpty() && eligible.all { it in selectedSet } -> ToggleableState.On
                                    eligible.any { it in selectedSet } -> ToggleableState.Indeterminate
                                    else -> ToggleableState.Off
                                }, onClick = { toggle(eligible) }, enabled = ready && eligible.isNotEmpty(),
                                    modifier = Modifier.semantics { contentDescription = "Выбрать группу ${group.title}" })
                                Text(group.title, Modifier.weight(1f), color = Fc.Text, style = MaterialTheme.typography.titleSmall)
                                Text("${exercises.size}", color = Fc.Muted)
                                IconButton(onClick = {
                                    if (query.isBlank()) openGroups = ArrayList(if (expanded) openGroups - group.name else openGroups + group.name)
                                    else collapsedSearch = ArrayList(if (expanded) collapsedSearch + group.name else collapsedSearch - group.name)
                                }, modifier = Modifier.testTag("picker-group-${group.name}").semantics {
                                    contentDescription = "${if (expanded) "Свернуть" else "Развернуть"} ${group.title}"
                                }) { Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleLarge) }
                            }
                        }
                        if (expanded) items(exercises, key = { it.id }) { e ->
                            PickerExerciseRow(e, e.id in selectedSet, e.id in already) { toggle(listOf(e.id)) }
                        }
                    }
                }
            }
            if (shown.isEmpty() && !reviewing) item { Text("Ничего не найдено. Попробуйте другое название.", color = Fc.Muted) }
        }
        Surface(shadowElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Выбрано: ${validSelected.size}", Modifier.weight(1f), color = Fc.Text)
                    TextButton(onClick = { reviewing = !reviewing }, enabled = validSelected.isNotEmpty() || reviewing) {
                        Text(if (reviewing) "К поиску" else "Проверить выбор")
                    }
                }
                if (hidden > 0 && !reviewing) Text("Вне текущего поиска: $hidden", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { onPick(validSelected) }, enabled = validSelected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("picker-add")) {
                    Text("Добавить ${validSelected.size} упражнений")
                }
            }
        }
    }
    }
}

@Composable
private fun PickerExerciseRow(e: ExerciseEntity, checked: Boolean, already: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).testTag("picker-exercise-${e.id}")
        .toggleable(value = checked || already, enabled = !already, role = Role.Checkbox, onValueChange = { onToggle() })
        .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked || already, onCheckedChange = null, enabled = !already)
        ExerciseThumbnail(e, Modifier.width(72.dp).height(56.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(e.name, color = Fc.Text, style = MaterialTheme.typography.titleSmall)
            Text(if (already) "Уже в этом дне" else "${e.group.title} · ${e.equipment}", color = Fc.Muted,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

