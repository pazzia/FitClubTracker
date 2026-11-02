---
name: ux-check
description: "Проверка экрана FitClub: снять скриншоты Robolectric (360 и 320 dp, шрифт 200 %, светлая и тёмная темы), затем ревью по теме, доступности и удобству в зале. Используйте после вёрстки экрана, перед мержем UI-изменений или когда пользователь спрашивает «нормально ли выглядит»."
argument-hint: "[экран или путь, например 'workout' или ui/today/TodayScreen.kt]"
---

# /ux-check

Экран: `$ARGUMENTS`.

## 1. Скриншоты

Без скриншотов визуальная часть получает **NOT ASSESSED**.

**A. Robolectric (без эмулятора).** В проекте уже есть образец: `app/src/test/java/com/fitclub/tracker/ui/Preview66UiTest.kt` (`@GraphicsMode(NATIVE)`, `captureToImage()`, PNG в `app/build/ui-preview/`). Создайте по нему временный тест `app/src/test/java/com/fitclub/tracker/ui/uxcheck/<Экран>UxCheckTest.kt`, он не коммитится и лежит в `.gitignore`. Матрица:
- `@Config(qualifiers = "w360dp-h780dp")`, `"w320dp-h640dp"`, `"w411dp-h891dp"`;
- масштаб шрифта 1.0 и 2.0: `RuntimeEnvironment.setFontScale(2.0f)` до `setContent`, или оберните контент в `CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f))`;
- тема: `FitClubTheme(lightTheme = true | false)`;
- для экранов тренировки — состояние с активной тренировкой, суперсетом и дропсетом.

Запустите `gradle testDebugUnitTest --tests '*UxCheckTest*'` и откройте PNG через Read.

**B. Эмулятор или телефон.** `adb exec-out screencap -p > shot.png`, масштаб шрифта — `adb shell settings put system font_scale 2.0`. Тестовая сборка ставится рядом с основной: `gradle -PfitclubExperimental=true assembleDebug`.

**C. Ничего не доступно** (нет Gradle или SDK). Скажите прямо и переходите к шагу 2 с одним кодом. Скриншоты можно получить позже из CI.

## 2. Ревью

Передайте агенту `ux-reviewer` код экрана, спеку (если есть) и пути к PNG. Проверьте, что каждая проблема привязана к `файл:строка` или к скриншоту и что визуальные пункты без скриншотов отмечены NOT ASSESSED.

## 3. Отчёт

Сначала вердикт и критичные проблемы, затем остальное, и 1–2 показательных скриншота. Исправляйте только после согласия пользователя. После исправления повторите шаг 1 для затронутых комбинаций. Временные тесты и `app/build/ui-preview` не коммитьте.
