# FitClub — правила для Claude Code

Android-трекер тренировок: программы, тренировки, статистика, справочник упражнений и атлас мышц.
Kotlin · Jetpack Compose · Material 3 · Room · Navigation Compose. minSdk 28, UI только на русском.
Подробно: `README.md`, `FORK.md`, `docs/SIGNING.md`, `docs/INSTALL_UPDATE.md`, `BACKLOG.md`.

## Команды

В репозитории нет `gradle-wrapper.jar`, поэтому используется установленный Gradle, как в CI.

| Что | Команда |
|---|---|
| Юнит-тесты (Robolectric, включая миграции и UI) | `gradle testDebugUnitTest` |
| Основная сборка | `gradle assembleDebug` |
| Тестовая сборка (`com.fitclub.tracker.experimental`) | `gradle -PfitclubExperimental=true assembleDebug` |
| Пересборка атласа | `python3 tools/atlas/build_atlas.py fitness-mvp-verified.html exact-schematics.json` |

CI (`.github/workflows/android.yml`) проверяет изображения, подпись, тесты и собирает APK. Из `main` получается основная установка, из остальных веток — тестовая.

## Жёсткие правила

- **Данные пользователя святы.** Любое изменение `Entities.kt` требует `DB_SCHEMA_VERSION` +1, миграции `MIGRATION_N_N+1` и теста в `DatabaseMigrationTest`. `fallbackToDestructiveMigration` запрещён. Хук остановит коммит без миграции.
- **Подпись и идентификатор неизменны.** `applicationId = com.fitclub.tracker`, `versionCode` только растёт, `docs/debug-signing-sha256.txt` не меняется, ключи не читаются и не коммитятся (`docs/SIGNING.md`).
- **`main` — единственный продовый источник.** Не пушить в `main`, без force push. Слияние только с разрешения владельца.
- Цвета берутся из `Fc.*` и `FcPalette` в `ui/theme/Theme.kt`: так светлая и тёмная темы работают сами. Новые литералы `Color(0x…)` в экранах не добавлять.
- При смене версии обновить README: заголовок, versionCode и раздел «Что изменено».
- Не коммитить без просьбы пользователя.

## Как работаем

Вопрос → варианты → решение пользователя → черновик → одобрение. Перед изменением нескольких файлов покажите список изменений.

Визуальная задача не считается готовой, пока экран не проверен на скриншоте: ширина 360 dp, масштаб шрифта 200 %, светлая и тёмная темы. Если проверить нельзя — пишите **NOT ASSESSED**, а не «готово». Отсутствие проверки не равно успешной проверке.

Контент про анатомию и технику проверен ИИ и требует экспертной проверки. Не выдавайте его за проверенный специалистом.

## Агенты и скиллы

- Агенты (`.claude/agents/`): `ux-reviewer` (экраны и доступность), `qa-lead` (тест-план, Definition of Done), `content-reviewer` (упражнения, мышцы, медиа, лицензии). Все только читают код.
- Скиллы: `/ux-check`, `/room-migration`, `/bug-report`, `/release-checklist`.
- Правила по путям лежат в `.claude/rules/` и подгружаются сами.

## Чекпоинт длинной задачи

Для задачи дольше одной сессии ведите `.claude/state/active.md` (не коммитится): цель, что сделано, что дальше, открытые вопросы. Хук `SessionStart` показывает его после старта, `/clear` и сжатия контекста.
