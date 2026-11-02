---
name: release-checklist
description: "Проверка готовности FitClub к выпуску: versionCode и versionName, README и changelog, миграции и их тесты, подпись, тесты, CI, контент и лицензии. Используйте перед слиянием в main или когда пользователь говорит «готовим 0.x»."
argument-hint: "[версия, например 0.11.0]"
---

# /release-checklist

Готовим выпуск `$ARGUMENTS`. Канал распространения — подписанный debug APK из Actions. Из `main` получается `com.fitclub.tracker`, из остальных веток — тестовая установка.

Для каждого пункта запишите **PASS / FAIL / NOT ASSESSED** и доказательство: команду и её реальный вывод.

| # | Пункт | Как проверить |
|---|---|---|
| 1 | `versionCode` больше, чем в `origin/main`; `versionName` соответствует выпуску | `git diff origin/main -- app/build.gradle.kts` |
| 2 | `applicationId = "com.fitclub.tracker"` не изменился | тот же diff |
| 3 | README: заголовок, версия и versionCode совпадают с `build.gradle.kts`, есть раздел «Что изменено в X» | `head -5 README.md` и `grep -n versionCode app/build.gradle.kts` |
| 4 | Если менялась схема: `DB_SCHEMA_VERSION` повышен, миграция есть в `addMigrations`, есть `checkMigration(N)` | `git diff origin/main -- app/src/main/java/com/fitclub/tracker/data/db app/src/test/java/com/fitclub/tracker/data/DatabaseMigrationTest.kt` |
| 5 | `docs/debug-signing-sha256.txt` не менялся | `git diff --stat origin/main -- docs/debug-signing-sha256.txt` (пусто) |
| 6 | Тесты зелёные | `gradle testDebugUnitTest` или статус CI на PR |
| 7 | CI PR зелёный: проверка подписи, package и версии APK | GitHub Actions |
| 8 | ID упражнений и мышц не удалены и не переименованы; новые медиа с лицензией | агент `content-reviewer` (если менялся контент) |
| 9 | UI-изменения проверены `/ux-check` со скриншотами | отчёт или скриншоты |
| 10 | Известные ограничения записаны | раздел в README или changelog |

Затем подготовьте черновик раздела «Что изменено в X» для README по `git log origin/main..HEAD --oneline`. Пишите по-русски, для пользователя, в стиле существующих разделов: короткие пункты о том, что человек заметит.

Итог: **READY**, если все пункты PASS; **NOT READY**, если есть FAIL; **NOT ASSESSED**, если важное проверить не удалось. Слияние в `main` и выпуск — только по решению владельца.
