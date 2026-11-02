---
paths:
  - "app/src/main/java/com/fitclub/tracker/data/db/**"
  - "app/src/main/java/com/fitclub/tracker/data/repo/**"
  - "app/src/test/java/com/fitclub/tracker/data/DatabaseMigrationTest.kt"
---

# Правила базы данных (Room)

`exportSchema = false`, поэтому Room не сравнивает схемы. Все проверки держатся на тестах миграций и на этом правиле.

- Изменили таблицу, колонку, индекс или внешний ключ в `Entities.kt` — сделайте:
  1. `DB_SCHEMA_VERSION` +1 в `AppDatabase.kt`;
  2. `MIGRATION_N_N+1` рядом с остальными миграциями: SQL, который переводит старую базу в новую и сохраняет данные;
  3. добавьте её в `.addMigrations(...)`;
  4. добавьте `@Test fun migratesVersionN…() = checkMigration(N)` в `DatabaseMigrationTest` и, если нужно, расширьте `legacySchema` и проверки данных.
- `fallbackToDestructiveMigration*` запрещён: пользователи хранят историю тренировок годами, и APK обновляется поверх.
- SQL миграции должен в точности соответствовать тому, что Room ожидает от `@Entity`: типы (`INTEGER`/`REAL`/`TEXT`), `NOT NULL`, `DEFAULT`. Иначе при открытии будет `IllegalStateException: Migration didn't properly handle`. Тест миграции это ловит, поэтому запускайте `gradle testDebugUnitTest`.
- Новая колонка `NOT NULL` в существующей таблице требует `DEFAULT` в SQL. Значения по умолчанию должны иметь смысл для старых записей.
- Переименование и удаление колонки делаются через новую таблицу: `CREATE … / INSERT … SELECT / DROP / ALTER … RENAME`.
- Каждая миграция обёрнута тестом, иначе её как будто нет.
- Подробный порядок шагов — скилл `/room-migration`.
