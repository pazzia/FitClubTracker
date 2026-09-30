package com.fitclub.tracker.ui.startup

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.StartupState
import com.fitclub.tracker.ui.navigation.AppRoot
import com.fitclub.tracker.ui.theme.Fc
import kotlinx.coroutines.delay

object ReleaseNotes {
    const val VERSION = "0.5.0"
    val items = listOf(
        "Выбор программы открывается на отдельной странице.",
        "Ягодицы и квадрицепсы взяты из исходных мужского и женского атласов.",
        "Все поля подходов одинакового размера.",
        "Завершение упражнения: тоннаж, время и сравнение с рекордом за всю историю.",
        "Свободный переход между упражнениями и завершение тренировки в любой момент.",
        "Результаты неполной тренировки сохраняются в истории."
    )
}

@Composable
fun AppStartup(app: FitApp) {
    val state by app.startup.collectAsStateWithLifecycle()
    val prefs = remember { app.getSharedPreferences("startup", Context.MODE_PRIVATE) }
    var notesAccepted by rememberSaveable { mutableStateOf(prefs.getString("seenVersion", null) == ReleaseNotes.VERSION) }
    var minimumElapsed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(800); minimumElapsed = true }
    if (state == StartupState.Ready && minimumElapsed && notesAccepted) AppRoot()
    else StartupScreen(state, minimumElapsed, onContinue = {
        prefs.edit().putString("seenVersion", ReleaseNotes.VERSION).apply()
        notesAccepted = true
    }, onRetry = app::initialize)
}

@Composable
fun StartupScreen(state: StartupState, minimumElapsed: Boolean, onContinue: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Fc.Bg).systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Text("FitClub", color = Fc.Lime, style = MaterialTheme.typography.displayLarge)
        Text("Твой дневник тренировок", color = Fc.Text, style = MaterialTheme.typography.titleMedium)
        Text("Версия ${ReleaseNotes.VERSION}", color = Fc.Muted, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        if (state is StartupState.Failed) {
            Text("Не удалось загрузить приложение", color = Fc.Pink)
            Text(state.message, color = Fc.Muted)
            TextButton(onClick = onRetry) { Text("Повторить", color = Fc.Lime) }
        } else if (state != StartupState.Ready || !minimumElapsed) {
            CircularProgressIndicator(color = Fc.Lime, modifier = Modifier.size(32.dp))
            Text("Загружаем программы и справочник…", color = Fc.Muted, modifier = Modifier.padding(top = 12.dp))
        } else Text("Всё готово к тренировке", color = Fc.Lime)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("Что нового", color = Fc.Text, style = MaterialTheme.typography.headlineSmall) }
            ReleaseNotes.items.forEach { note -> item { Text("• $note", color = Fc.Muted) } }
        }
        Button(onClick = onContinue, enabled = state == StartupState.Ready && minimumElapsed,
            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Fc.Lime)) { Text("Продолжить") }
    }
}
