package com.fitclub.tracker.ui.programs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitclub.tracker.AppContainer
import com.fitclub.tracker.data.db.ExerciseEntity
import com.fitclub.tracker.data.model.ExerciseType
import com.fitclub.tracker.data.model.MuscleGroup
import com.fitclub.tracker.data.seed.FreeExerciseCatalog
import com.fitclub.tracker.ui.components.*
import com.fitclub.tracker.ui.navigation.appViewModel
import com.fitclub.tracker.ui.theme.Fc

private class PickerProfileViewModel(c: AppContainer) : ViewModel() { val settings = c.profile.settings }

@Composable
internal fun ExercisePicker(
    catalog: List<ExerciseEntity>, already: Set<String>, subtitle: String,
    onBack: () -> Unit, onPick: (String) -> Unit
) {
    val vm = appViewModel { PickerProfileViewModel(it) }
    val profile by vm.settings.collectAsStateWithLifecycle()
    var group by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<ExerciseType?>(null) }
    fun categories() { group = null; query = ""; type = null }
    BackHandler(enabled = group != null) { categories() }
    val selectedGroup = group
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BackRow(if (selectedGroup == null) "К программе" else "Группы мышц") {
            if (selectedGroup == null) onBack() else categories()
        } }
        item {
            ScreenTitle(selectedGroup?.title ?: "Добавить упражнение")
            Text(subtitle, color = Fc.Muted, style = MaterialTheme.typography.bodyMedium)
            if (selectedGroup != null) Text("${catalog.count { it.group == selectedGroup }} упражнений", color = Fc.Muted)
        }
        if (selectedGroup == null) {
            item { Text("Выберите группу мышц", color = Fc.Text, style = MaterialTheme.typography.titleMedium) }
            val counts = catalog.groupingBy { it.group }.eachCount()
            items(AnatomyGroupOrder.chunked(2)) { pair ->
                MuscleCategoryRow(pair, profile.sex, counts) { group = it; query = ""; type = null }
            }
        } else {
            item { GroupImage(profile.sex, selectedGroup, Modifier.fillMaxWidth().height(180.dp)) }
            if (selectedGroup == MuscleGroup.QUADS) item { QuadricepsLegend() }
            item {
                OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск в категории…") }, leadingIcon = { Icon(Icons.Filled.Search, null) })
            }
            item {
                Segmented(listOf(null, ExerciseType.BASIC, ExerciseType.ISOLATION), type, { type = it },
                    { when (it) { null -> "Все"; ExerciseType.BASIC -> "Базовые"; else -> "Изоляция" } }, selectedBg = Fc.Lime)
            }
            val shown = catalog.filter { it.group == selectedGroup && (type == null ||
                (it.type == type && FreeExerciseCatalog.media[it.id]?.category != "Растяжка")) &&
                it.name.contains(query.trim(), ignoreCase = true) }
            if (shown.isEmpty()) item { Text("Нет упражнений с такими фильтрами", color = Fc.Muted) }
            items(shown, key = { it.id }) { exercise ->
                val has = exercise.id in already
                FcCard(onClick = if (has) null else ({ onPick(exercise.id) })) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GroupImage(profile.sex, selectedGroup, Modifier.size(52.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(exercise.name, color = Fc.Text, style = MaterialTheme.typography.titleSmall)
                            val category = FreeExerciseCatalog.media[exercise.id]?.category
                            Text("${if (category == "Растяжка") category else exercise.type.title} · ${exercise.equipment}", color = Fc.Muted, style = MaterialTheme.typography.bodySmall)
                            if (has) Text("Уже в этом дне", color = Fc.Lime, style = MaterialTheme.typography.labelSmall)
                        }
                        Icon(if (has) Icons.Filled.Check else Icons.Filled.Add,
                            contentDescription = if (has) "Уже выбрано" else "Добавить ${exercise.name}", tint = Fc.Lime)
                    }
                }
            }
        }
    }
}
