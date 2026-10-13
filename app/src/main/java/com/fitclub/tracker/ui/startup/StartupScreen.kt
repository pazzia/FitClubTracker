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
import com.fitclub.tracker.BuildConfig
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.StartupState
import com.fitclub.tracker.ui.navigation.AppRoot
import com.fitclub.tracker.ui.theme.Fc
import com.fitclub.tracker.ui.components.glow
import kotlinx.coroutines.delay

object ReleaseNotes {
    const val VERSION = BuildConfig.VERSION_NAME
    /** Newest first. After an update only the versions since the last one seen are shown. */
    val history: List<Pair<String, List<String>>> = listOf(
        "0.11.0" to listOf(
            "Новый дизайн Iron & Lime: лаймовые кнопки-капсулы с мягким свечением, карточки 20dp, тёмный фон с подсветкой.",
            "Экран «Сегодня» по новому макету: аватар, серия недель, карточка тренировки с фото и прогрессом, план недели плитками.",
            "Перед тренировкой — отсчёт 3-2-1-GO с кольцом и вибрацией. Нажатие на экран начинает сразу; выключается в профиле.",
            "После тренировки — анимированный итог: галочка, кольца, время, упражнения, тоннаж и серия.",
            "Профиль стал пятой вкладкой нижнего меню."
        ),
        "0.10.0" to listOf(
            "Первое знакомство: пол, единицы, опыт и сколько тренировок в неделю — программа подберётся сама.",
            "«Прогресс» разделён на «Силу», «Тело» и «Регулярность»; в «Силе» — график оценки 1ПМ по выбранному упражнению.",
            "В замерах тела рядом с обхватами — вес и процент жира, с карточками изменений и графиком.",
            "Простой ввод подходов для новичков: только вес, повторы и «Готово». Подробный включается в настройках.",
            "Подсказка дисков на штангу: «Гриф 20 + на сторону 25 + 2,5».",
            "Новый рекорд в итоге тренировки — крупно, на лаймовой плашке.",
            "Справочник открывается ростовым атласом, группы мышц показаны вашими иллюстрациями атласа.",
            "Подписи без капса, цвета и шрифты из единой темы."
        ),
        "0.9.6" to listOf("Замеры тела: фигуры целиком, масштаб под ширину экрана."),
        "0.9.5" to listOf("Замеры тела по новому дизайну: обхваты на фигуре, карточки изменений, график за 8 недель, новый замер «Плечи»."),
        "0.9.4" to listOf("Звук в конце отдыха, включается в Личном кабинете."),
        "0.9.3" to listOf("Модель тела в профиле и пошаговый ввод замера."),
        "0.9.2" to listOf("Вес переносится из прошлого подхода, суперсет внутри подхода, понятные дропсеты, экраны под любую ширину."),
        "0.9.1" to listOf("Атлас мышц: 120 карточек и упражнения для каждой мышцы.")
    )
    /** Compatibility: the notes of the current version. */
    val items: List<String> get() = history.first().second

    private fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    fun newer(a: String, b: String): Boolean {
        val x = parts(a); val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) { val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }; if (d != 0) return d > 0 }
        return false
    }
    /** What changed since [seen]; on a fresh install only the current version. */
    fun since(seen: String?): List<Pair<String, List<String>>> =
        if (seen == null) history.take(1) else history.filter { newer(it.first, seen) }.ifEmpty { history.take(1) }
}

@Composable
fun AppStartup(app: FitApp) {
    val state by app.startup.collectAsStateWithLifecycle()
    val prefs = remember { app.getSharedPreferences("startup", Context.MODE_PRIVATE) }
    var notesAccepted by rememberSaveable { mutableStateOf(prefs.getString("seenVersion", null) == ReleaseNotes.VERSION) }
    // The short pause exists only to read release notes; a returning user goes straight in.
    var minimumElapsed by rememberSaveable { mutableStateOf(notesAccepted) }
    LaunchedEffect(Unit) { if (!minimumElapsed) { delay(800); minimumElapsed = true } }
    val seen = remember { prefs.getString("seenVersion", null) }
    // A fresh install gets three questions instead of release notes.
    var onboarding by rememberSaveable { mutableStateOf(seen == null && !prefs.getBoolean("onboarded", false)) }
    if (state == StartupState.Ready && onboarding) {
        OnboardingScreen(app) {
            prefs.edit().putBoolean("onboarded", true).putString("seenVersion", ReleaseNotes.VERSION).apply()
            onboarding = false; notesAccepted = true; minimumElapsed = true
        }
    } else if (state == StartupState.Ready && minimumElapsed && notesAccepted) AppRoot()
    else StartupScreen(state, minimumElapsed, onContinue = {
        prefs.edit().putString("seenVersion", ReleaseNotes.VERSION).apply()
        notesAccepted = true
    }, onRetry = app::initialize, notes = ReleaseNotes.since(seen))
}

@Composable
fun StartupScreen(state: StartupState, minimumElapsed: Boolean, onContinue: () -> Unit, onRetry: () -> Unit,
    notes: List<Pair<String, List<String>>> = ReleaseNotes.since(null)) {
    Column(Modifier.fillMaxSize().background(Fc.Bg).systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Text("FitClub", color = Fc.Lime, style = MaterialTheme.typography.displayLarge)
        Text("Твой дневник тренировок", color = Fc.Text, style = MaterialTheme.typography.titleMedium)
        Text("Версия ${ReleaseNotes.VERSION}" + (if (BuildConfig.BUILD_NUMBER != "local") " · сборка ${BuildConfig.BUILD_NUMBER}" else ""), color = Fc.Muted, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
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
            notes.forEach { (version, list) ->
                if (notes.size > 1) item { Text("Версия $version", color = Fc.Text, style = MaterialTheme.typography.titleSmall) }
                list.forEach { note -> item { Text("• $note", color = Fc.Muted) } }
            }
        }
        Button(onClick = onContinue, enabled = state == StartupState.Ready && minimumElapsed,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .glow(Fc.Lime, state == StartupState.Ready && minimumElapsed && !Fc.isLight),
            colors = ButtonDefaults.buttonColors(containerColor = Fc.Lime, contentColor = Fc.OnAccent)) {
            Text("Продолжить", style = MaterialTheme.typography.titleMedium)
        }
    }
}

