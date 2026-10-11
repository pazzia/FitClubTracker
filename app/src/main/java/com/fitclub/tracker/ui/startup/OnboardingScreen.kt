package com.fitclub.tracker.ui.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.data.model.Goal
import com.fitclub.tracker.data.model.RepRange
import com.fitclub.tracker.data.model.Sex
import com.fitclub.tracker.data.repo.DayDraft
import com.fitclub.tracker.data.repo.ItemDraft
import com.fitclub.tracker.data.repo.ProgramDraft
import com.fitclub.tracker.data.repo.ProgramIcons
import com.fitclub.tracker.data.repo.SamplePrograms
import com.fitclub.tracker.ui.components.PrimaryButton
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Answers of the first launch. */
data class OnboardingAnswers(val sex: Sex, val unitsKg: Boolean, val novice: Boolean, val perWeek: Int)

/** The program that matches how often a person trains: 2 → full body A/B, 3 → full body A/B/C, 4 → upper/lower. */
fun starterProgram(perWeek: Int): ProgramDraft {
    fun i(ex: String, sets: Int, reps: RepRange, rest: Int) = ItemDraft(ex, sets, reps, rest)
    val fullBody = SamplePrograms.all.first()
    return when (perWeek) {
        2 -> fullBody.copy(name = "Фулбоди, 2 дня", freqPerWeek = 2, isActive = true, days = fullBody.days.take(2))
        4 -> ProgramDraft(
            name = "Верх и низ, 4 дня", goal = Goal.MASS, colorArgb = 0xFF3B8BFF, icon = ProgramIcons.MOUNTAIN, freqPerWeek = 4, isActive = true,
            days = listOf(
                DayDraft(listOf(i("bench", 4, RepRange.R6_8, 120), i("bbrow", 4, RepRange.R8_12, 90), i("ohp", 3, RepRange.R8_12, 90),
                    i("latpull", 3, RepRange.R8_12, 90), i("bbcurl", 3, RepRange.R8_12, 60), i("skull", 3, RepRange.R8_12, 60))),
                DayDraft(listOf(i("squat", 4, RepRange.R6_8, 150), i("rdl", 4, RepRange.R8_12, 120), i("legpress", 3, RepRange.R12_15, 90),
                    i("plank", 3, RepRange.TIME, 60)))
            ))
        else -> fullBody.copy(isActive = true)
    }
}

/**
 * First launch: three short questions, then a program that matches the answers is made active.
 * Every answer can be changed later in the profile.
 */
@Composable
fun OnboardingScreen(app: FitApp, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var sex by rememberSaveable { mutableStateOf(Sex.MALE) }
    var unitsKg by rememberSaveable { mutableStateOf(true) }
    var novice by rememberSaveable { mutableStateOf(true) }
    var perWeek by rememberSaveable { mutableIntStateOf(3) }
    var saving by remember { mutableStateOf(false) }

    fun finish() {
        if (saving) return
        saving = true
        scope.launch {
            val c = app.container
            c.profile.update(c.profile.settings.value.copy(sex = sex, unitsKg = unitsKg, level = if (novice) 1 else 2, advancedSets = !novice))
            runCatching {
                val draft = starterProgram(perWeek)
                // Three days a week is the bundled full-body program: activate it instead of a copy.
                val existing = c.programs.observeAll().first().firstOrNull { it.name == draft.name }
                if (existing != null) c.programs.setActive(existing.id) else c.programs.save(draft)
            }
            onDone()
        }
    }

    Column(
        Modifier.fillMaxSize().background(Fc.Bg).systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            repeat(3) { i -> Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= step) Fc.Lime else Fc.Line)) }
        }
        Text("Шаг ${step + 1} из 3", color = Fc.Muted, style = MaterialTheme.typography.labelLarge)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                0 -> {
                    Question("Кого показывать на фигурах?", "Фигура нужна для замеров тела и атласа мышц.")
                    Choices(listOf(Sex.MALE to "Мужчина", Sex.FEMALE to "Женщина"), sex) { sex = it }
                    Text("Единицы веса", color = Fc.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    Choices(listOf(true to "Килограммы", false to "Фунты"), unitsKg) { unitsKg = it }
                }
                1 -> {
                    Question("Какой у вас опыт?", "От этого зависит, сколько всего показывать во время тренировки.")
                    Choices(listOf(true to "Начинаю", false to "Тренируюсь давно"), novice, notes = mapOf(
                        true to "Только вес, повторы и «Готово»",
                        false to "Разминка, дропсеты, суперсеты, RIR"
                    )) { novice = it }
                }
                else -> {
                    Question("Сколько тренировок в неделю?", "Подберём программу, её дни будут чередоваться сами.")
                    Choices(listOf(2 to "2 раза", 3 to "3 раза", 4 to "4 раза"), perWeek, notes = mapOf(
                        2 to "Фулбоди, 2 дня: всё тело за тренировку",
                        3 to "Фулбоди, 3 дня: всё тело, три разных дня",
                        4 to "Верх и низ, 4 дня: чередование верха и ног"
                    )) { perWeek = it }
                }
            }
        }
        PrimaryButton(if (step < 2) "Дальше" else if (saving) "Подбираю программу…" else "Начать",
            { if (step < 2) step++ else finish() }, modifier = Modifier.testTag("onboarding-next"))
        if (step > 0) TextButton(onClick = { step-- }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Назад", color = Fc.Muted) }
    }
}

@Composable
private fun Question(title: String, hint: String) {
    Text(title, color = Fc.Text, style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
    Text(hint, color = Fc.Muted, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun <T> Choices(options: List<Pair<T, String>>, selected: T, notes: Map<T, String> = emptyMap(), onSelect: (T) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (value, title) ->
            val on = value == selected
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape)
                    .background(if (on) Fc.LimeSoft else Fc.Surface)
                    .border(if (on) 2.dp else 1.dp, if (on) Fc.Lime else Fc.Line, shape)
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(title, color = Fc.Text, style = MaterialTheme.typography.titleMedium)
                notes[value]?.let { Text(it, color = Fc.Muted, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
