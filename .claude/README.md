# .claude — настройка Claude Code для FitClub

Главные правила — в `CLAUDE.md` в корне. Здесь лежит то, что их обеспечивает.

| Папка | Что внутри |
|---|---|
| `settings.json` | Запреты (`rm -rf`, force push, пуш в main, `reset --hard`, чтение ключей) и подключение хуков |
| `hooks/` | `guard-bash.sh` проверяет пуши и проверяет коммит: схема Room без `DB_SCHEMA_VERSION` и миграции, падение `versionCode`, смена `applicationId`, файлы подписи. `guard-write.sh` защищает отпечаток подписи и ключи. `post-edit.sh` напоминает про Room, тему `Fc`, размер касаний, README при смене версии и атлас. `session-start.sh` показывает состояние ветки и чекпоинт |
| `rules/` | Правила по путям: `room.md`, `ui.md`, `content.md` |
| `agents/` | `ux-reviewer`, `qa-lead`, `content-reviewer` — только читают, код не правят |
| `skills/` | `/ux-check`, `/room-migration`, `/bug-report`, `/release-checklist` |
| `templates/` | `ux-spec.md` — спека экрана до вёрстки |

Хукам нужны `bash`, `git` и `python3`. Если изменение `Entities.kt` не трогает схему, добавьте `[schema-unchanged]` в сообщение коммита.

Чтобы временно выключить хуки у себя, используйте `.claude/settings.local.json` (не коммитится).

Идеи взяты из [Claude Code Game Studios](https://github.com/Donchitos/Claude-Code-Game-Studios) (MIT): протокол «вопрос → варианты → решение → черновик → одобрение», правила по путям, вердикт NOT ASSESSED, обязательный скриншот для визуальных задач, чекпоинт сессии. Тексты переписаны под этот проект.
