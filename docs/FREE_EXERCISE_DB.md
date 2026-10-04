# Free Exercise DB in FitClub 0.6.0

220 curated source records: 20 per each of the 11 existing muscle groups.
440 original JPEG photographs are bundled inside the APK, with manual photo switching.
The Gradle preBuild task downloads source commit f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5,
extracts only the selected photographs, and verifies every SHA-256 in photos.sha256.
The archive is cached in .gradle/free-exercise-db for subsequent builds.
All entries have Russian names and abridged Russian technique descriptions.
Original English metadata and instructions are retained in assets/free_exercise_db/catalog.json.
Existing exercise IDs, programs, workouts, and descriptions are preserved. Startup inserts missing
catalog entries into populated installations too; repeated startup is idempotent.

Source: https://github.com/yuhonas/free-exercise-db (876 source records, retrieved 2026-10-03).
The upstream repository declares the Unlicense/public-domain dataset in its README.
Photos are unchanged; anatomical category artwork is unchanged.
Review the source record and both images when changing the adaptation.

## Category boundaries

14 glute records list glutes as a primary target; 6 as a secondary target.
The source primary and secondary muscles are preserved and visible in the exercise card.
The calf selection includes 15 strength entries and 5 clearly labeled stretches.
The stretches appear under "Все" and are excluded from "Базовые" / "Изоляция" filters.
No source exercises are duplicated under multiple groups to meet the quota.
Aliases reuse existing IDs for equivalent movements, avoiding duplicate catalog rows.

## Videos

220 YouTube links come from upstream PR #26, commit fe40028def68793fbd5c652402420a4c63d28a1e:
https://github.com/yuhonas/free-exercise-db/pull/26
They are opened through the system browser/YouTube; internet is required.
They are source-provided tutorial links and may become unavailable or show a related variation.
No YouTube footage is downloaded or redistributed. The main dataset contains no GIF/MP4 assets.
Third-party Gym Visual GIF datasets do not grant downstream media redistribution rights,
so those files are not bundled. Third-party video datasets with uncertain provenance are not bundled.

## Reproduce

Unpack the upstream repository, download the pinned PR JSON, then run:

```sh
python tools/import_free_exercise_db.py /path/to/free-exercise-db /path/to/video-exercises.json
```

Review tools/exercise_selection.tsv. The importer verifies unique source IDs, target muscles,
20 records per group, and image paths, and emits Kotlin catalog, original JSON, photos, and
docs/FREE_EXERCISE_DB_IMPORT.json. The latter records the source hash and counts.

## Verification

ExerciseCatalogTest checks coverage, bundled image dimensions, video link format, incremental
import into a populated database, preserved existing rows, and idempotency. ProgramScorerTest
checks existing sample program IDs and score behavior against the expanded catalog.
