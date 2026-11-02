#!/usr/bin/env bash
# PostToolUse(Write|Edit): reminders for Room, theme tokens and versions.
# Never blocks; only adds context for Claude.
source "$(dirname "$0")/lib.sh"
read_input
path="$(edited_path)"
text="$(added_text)"
ui=app/src/main/java/com/fitclub/tracker/ui
db=app/src/main/java/com/fitclub/tracker/data/db
msgs=()

if [[ "$path" == "$db/Entities.kt" ]]; then
  msgs+=("Изменена схема Room. Если меняются таблицы или колонки: DB_SCHEMA_VERSION +1, MIGRATION_N_N+1 в AppDatabase, addMigrations(...), тест в DatabaseMigrationTest (checkMigration(N)). Без этого коммит остановит хук.")
fi

if [[ "$path" == "$ui"/*.kt && "$path" != "$ui/theme/"* ]]; then
  if grep -Eq 'Color\(0x' <<<"$text"; then
    msgs+=("Цвет задан литералом вне ui/theme ($path). Используйте Fc.* из Theme.kt (он учитывает светлую и тёмную темы); новый цвет — в FcPalette.")
  fi
  if grep -Eq 'fontSize *= *[0-9]+(\.[0-9]+)?\.sp' <<<"$text"; then
    msgs+=("fontSize задан числом ($path). Предпочтительно MaterialTheme.typography.* или NumberStyle из Theme.kt.")
  fi
  if grep -Eq '(height|size)\((3[0-9]|4[0-7])\.dp\)' <<<"$text" && grep -Eq 'clickable|onClick|Button' <<<"$text"; then
    msgs+=("Похоже на нажимаемый элемент меньше 48 dp ($path). Минимум касания — 48 dp, основные кнопки — 56 dp.")
  fi
fi

if [[ "$path" == app/src/main/assets/atlas/atlas.json ]]; then
  msgs+=("atlas.json собирается tools/atlas/build_atlas.py из внешнего HTML (в репозитории его нет). Ручная правка пропадёт при следующей пересборке: перенесите исправление и в источник или в build_atlas.py, а проверку добавьте в MuscleAtlasTest.")
fi

if [[ "$path" == app/build.gradle.kts ]] && grep -Eq 'versionName|versionCode' <<<"$text"; then
  msgs+=("Изменена версия. Обновите заголовок и раздел «Что изменено» в README.md (версия и versionCode там указаны явно).")
fi

[ ${#msgs[@]} -eq 0 ] && exit 0
note PostToolUse "$(printf '%s\n' "${msgs[@]}")"
