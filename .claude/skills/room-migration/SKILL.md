---
name: room-migration
description: "Пошаговое изменение схемы Room в FitClub без потери данных: сущность, DB_SCHEMA_VERSION, миграция, addMigrations, тест checkMigration. Используйте, когда нужно добавить, переименовать или удалить таблицу или колонку, индекс или связь."
argument-hint: "[что меняем, например 'добавить колонку rpe в set_logs']"
---

# /room-migration

Изменение: `$ARGUMENTS`.

Файлы: `app/src/main/java/com/fitclub/tracker/data/db/Entities.kt`, `AppDatabase.kt`, тест `app/src/test/java/com/fitclub/tracker/data/DatabaseMigrationTest.kt`.

## 1. План (покажите пользователю до правок)

- Текущая версия: `DB_SCHEMA_VERSION` в `AppDatabase.kt`. Обозначим её N, новая — N+1.
- Какие таблицы и колонки меняются, какие значения получат существующие записи.
- Тип операции:
  - **добавить колонку** — `ALTER TABLE x ADD COLUMN c TYPE NOT NULL DEFAULT v` (для nullable без `NOT NULL`);
  - **новая таблица** — `CREATE TABLE` в точности, как Room генерирует её для `@Entity`, плюс индексы;
  - **переименовать или удалить колонку, сменить тип** — новая таблица `x_new`, `INSERT INTO x_new SELECT …`, `DROP TABLE x`, `ALTER TABLE x_new RENAME TO x`, затем пересоздать индексы.

## 2. Правки

1. `@Entity` в `Entities.kt`.
2. `DB_SCHEMA_VERSION = N+1`.
3. `val MIGRATION_N_N1 = object : Migration(N, N+1) { override fun migrate(db) { … } }` рядом с остальными миграциями. Имя — по образцу `MIGRATION_10_11`.
4. Добавьте её в конец `.addMigrations(...)`.
5. Если меняются DAO или репозитории — их тоже.

Типы SQL должны совпадать с ожиданиями Room: `Int`/`Long`/`Boolean` → `INTEGER`, `Float`/`Double` → `REAL`, `String` → `TEXT`. Nullability и `DEFAULT` — как в `@ColumnInfo(defaultValue = …)`. Иначе приложение упадёт на открытии с `Migration didn't properly handle`.

## 3. Тест

В `DatabaseMigrationTest` добавьте `@Test fun migratesVersionN…() = checkMigration(N)`. Если тест создаёт старую схему из `legacySchema`, убедитесь, что версия N там представлена: таблица в старом виде плюс вставка данных, которые должны пережить миграцию. Проверьте, что данные на месте после открытия базы текущей версией.

## 4. Проверка

`gradle testDebugUnitTest --tests '*DatabaseMigrationTest*'`, затем весь `gradle testDebugUnitTest`. Покажите реальный вывод. Если Gradle недоступен, итог **NOT ASSESSED** до зелёного CI на PR.

Коммит сделает хук-проверку: при изменённом `Entities.kt` без повышения версии или без `MIGRATION_N_N+1` он остановит коммит.
