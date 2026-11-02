#!/usr/bin/env bash
# PreToolUse(Bash): git safety + checks before `git commit`.
# Blocks: pushes to main/master, force pushes.
# On `git commit`: Room schema bump, versionCode, signing files.
source "$(dirname "$0")/lib.sh"
read_input
cmd="$(json_field tool_input.command)"
[ -n "$cmd" ] || exit 0

# --- git push -----------------------------------------------------------------
if grep -Eq '(^|[;&|[:space:]])git([[:space:]]+-[^[:space:]]+)*[[:space:]]+push([[:space:]]|$)' <<<"$cmd"; then
  if grep -Eq '(--force|[[:space:]]-f([[:space:]]|$)|[[:space:]]\+[^[:space:]]+)' <<<"$cmd"; then
    block "Force push запрещён в этом проекте. Если историю действительно нужно переписать, это делает владелец вручную."
  fi
  if grep -Eq '(:|[[:space:]])(refs/heads/)?(main|master)([[:space:]]|$)' <<<"$cmd"; then
    block "Пуш в main запрещён: main — единственный продовый источник (CI собирает из него com.fitclub.tracker). Изменения попадают туда только через PR с разрешения владельца."
  fi
  branch="$(git rev-parse --abbrev-ref HEAD 2>/dev/null)"
  if [[ "$branch" == main || "$branch" == master ]] && ! grep -Eq 'push[[:space:]]+(-[^[:space:]]+[[:space:]]+)*[^-[:space:]]+[[:space:]]+[^-[:space:]]' <<<"$cmd"; then
    block "Текущая ветка — $branch, а пуш без явной ветки ушёл бы в неё. Создайте рабочую ветку."
  fi
fi

# --- git commit ---------------------------------------------------------------
if grep -Eq '(^|[;&|[:space:]])git([[:space:]]+-[^[:space:]]+)*[[:space:]]+commit([[:space:]]|$)' <<<"$cmd"; then
  staged="$(git diff --cached --name-only 2>/dev/null)"
  diffopt=(--cached)
  if grep -Eq 'commit[[:space:]].*(-a[[:space:]]|-a$|-am|--all)' <<<"$cmd"; then
    staged="$staged"$'\n'"$(git diff --name-only 2>/dev/null)"
    diffopt=(HEAD)
  fi

  # Signing: the pinned fingerprint and keystores never change in a commit.
  if grep -Eq '(^|/)(debug-signing-sha256\.txt|[^/]*\.(jks|keystore))$' <<<"$staged"; then
    block "В коммите файл подписи (debug-signing-sha256.txt или keystore). Отпечаток закреплён намеренно, ключи в репозиторий не попадают — см. docs/SIGNING.md. Уберите файл из коммита."
  fi

  db=app/src/main/java/com/fitclub/tracker/data/db
  # Room: entity changes need DB_SCHEMA_VERSION + migration (exportSchema=false, so Room won't tell us).
  if grep -q "^$db/Entities.kt" <<<"$staged" && ! grep -q 'schema-unchanged' <<<"$cmd"; then
    entity_diff="$(git diff "${diffopt[@]}" -- "$db/Entities.kt" | grep -E '^[+-][^+-]' | grep -Ev '^[+-][[:space:]]*(//|/\*|\*)')"
    version_diff="$(git diff "${diffopt[@]}" -- "$db/AppDatabase.kt" | grep -E '^\+.*DB_SCHEMA_VERSION *= *[0-9]+')"
    if [ -n "$entity_diff" ] && [ -z "$version_diff" ]; then
      block "Изменён Entities.kt, но DB_SCHEMA_VERSION не повышен. Нужно: +1 к DB_SCHEMA_VERSION, MIGRATION_N_N+1 в AppDatabase, добавить её в addMigrations(...) и тест в DatabaseMigrationTest. Если изменение не трогает схему (переименование в Kotlin, @Ignore и т.п.), повторите коммит с пометкой [schema-unchanged] в сообщении."
    fi
  fi
  if grep -q "^$db/AppDatabase.kt" <<<"$staged"; then
    new_ver="$(git diff "${diffopt[@]}" -- "$db/AppDatabase.kt" | sed -nE 's/^\+.*DB_SCHEMA_VERSION *= *([0-9]+).*/\1/p' | head -1)"
    if [ -n "$new_ver" ]; then
      prev=$((new_ver - 1))
      if ! git show ":$db/AppDatabase.kt" 2>/dev/null | grep -q "MIGRATION_${prev}_${new_ver}" && ! grep -q "MIGRATION_${prev}_${new_ver}" "$db/AppDatabase.kt"; then
        block "DB_SCHEMA_VERSION = $new_ver, но MIGRATION_${prev}_${new_ver} не найдена. Без миграции у пользователей упадёт открытие базы или пропадут тренировки."
      fi
    fi
  fi

  # versionCode must grow: Android refuses to update otherwise.
  if grep -q '^app/build.gradle.kts' <<<"$staged"; then
    old_vc="$(git show HEAD:app/build.gradle.kts 2>/dev/null | sed -nE 's/^[[:space:]]*versionCode *= *([0-9]+).*/\1/p' | head -1)"
    new_vc="$(sed -nE 's/^[[:space:]]*versionCode *= *([0-9]+).*/\1/p' app/build.gradle.kts | head -1)"
    if [ -n "$old_vc" ] && [ -n "$new_vc" ] && [ "$new_vc" -lt "$old_vc" ]; then
      block "versionCode уменьшился ($old_vc → $new_vc). Android не обновит установленное приложение; versionCode только растёт."
    fi
    if git show HEAD:app/build.gradle.kts 2>/dev/null | grep -q 'applicationId = "com.fitclub.tracker"' && ! grep -q 'applicationId = "com.fitclub.tracker"' app/build.gradle.kts; then
      block "Изменён applicationId. Он должен оставаться com.fitclub.tracker, иначе это будет другое приложение без данных пользователя."
    fi
  fi
fi
exit 0
