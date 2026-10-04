"""Import the reviewed selection; preserve existing exercise IDs and original JSON.

Usage: python tools/import_free_exercise_db.py SOURCE_DIR VIDEO_JSON
SOURCE_DIR is an unpacked yuhonas/free-exercise-db snapshot.
VIDEO_JSON is dist/exercises.json from upstream PR #26 (fe40028d...).
"""
import hashlib
import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path(sys.argv[1])
VIDEOS = {e['id']: e for e in json.loads(Path(sys.argv[2]).read_text())}
DATA = {e['id']: e for e in json.loads((SOURCE / 'dist/exercises.json').read_text())}
ALIASES = {
    'Barbell_Bench_Press_-_Medium_Grip': 'bench', 'Dumbbell_Bench_Press': 'dbbench',
    'Incline_Dumbbell_Press': 'incline', 'Dumbbell_Flyes': 'flyes', 'Cable_Crossover': 'cross',
    'Dips_-_Chest_Version': 'dips', 'Pushups': 'pushup', 'Pullups': 'pullup',
    'Wide-Grip_Lat_Pulldown': 'latpull', 'Bent_Over_Barbell_Row': 'bbrow',
    'One-Arm_Dumbbell_Row': 'dbrow', 'Seated_Cable_Rows': 'cablerow',
    'Barbell_Deadlift': 'deadlift', 'Hyperextensions_Back_Extensions': 'hyper',
    'Standing_Military_Press': 'ohp', 'Seated_Dumbbell_Press': 'dbpress',
    'Side_Lateral_Raise': 'lateral', 'Face_Pull': 'facepull', 'Barbell_Curl': 'bbcurl',
    'Dumbbell_Bicep_Curl': 'dbcurl', 'Hammer_Curls': 'hammer', 'Preacher_Curl': 'preacher',
    'Close-Grip_Barbell_Bench_Press': 'closegrip', 'EZ-Bar_Skullcrusher': 'skull',
    'Triceps_Pushdown': 'pushdown', 'Seated_Triceps_Press': 'overhead',
    'Seated_Palm-Up_Barbell_Wrist_Curl': 'wristcurl', 'Barbell_Squat': 'squat',
    'Leg_Press': 'legpress', 'Goblet_Squat': 'goblet', 'Dumbbell_Lunges': 'lunge',
    'Leg_Extensions': 'legext', 'Front_Barbell_Squat': 'frontsquat',
    'Romanian_Deadlift': 'rdl', 'Lying_Leg_Curls': 'legcurl',
    'Natural_Glute_Ham_Raise': 'nordic', 'Barbell_Hip_Thrust': 'hipthrust',
    'One-Legged_Cable_Kickback': 'kickback', 'Standing_Calf_Raises': 'standcalf',
    'Seated_Calf_Raise': 'seatcalf', 'Calf_Press_On_The_Leg_Press_Machine': 'presscalf',
    'Plank': 'plank', 'Crunches': 'crunch', 'Hanging_Leg_Raise': 'hangraise',
    'Cable_Crunch': 'cablecrunch', 'Ab_Roller': 'wheel',
}
MUSCLES = {'chest':'CHEST', 'lats':'LATS', 'middle back':'MIDDLE_BACK', 'traps':'TRAPS', 'adductors':'ADDUCTORS',
           'lower back':'LOWER_BACK', 'shoulders':'DELTS', 'biceps':'BICEPS',
           'triceps':'TRICEPS', 'forearms':'FOREARMS', 'quadriceps':'QUADS',
           'hamstrings':'HAMS', 'glutes':'GLUTES', 'calves':'CALVES', 'abdominals':'ABS'}
EQUIPMENT = {'barbell':'Штанга', 'dumbbell':'Гантели', 'cable':'Блок',
             'machine':'Тренажёр', 'body only':'Свой вес', 'bands':'Резинка',
             'kettlebells':'Гиря', 'exercise ball':'Фитбол', 'other':'Другое'}
CATEGORIES = {'strength':'Силовое', 'powerlifting':'Пауэрлифтинг',
              'stretching':'Растяжка', 'plyometrics':'Плиометрика',
              'olympic weightlifting':'Тяжёлая атлетика', 'cardio':'Кардио', 'strongman':'Силовой экстрим'}
TARGETS = {'CHEST':['chest'], 'BACK':['lats','middle back','traps','lower back'],
           'SHOULDERS':['shoulders'], 'BICEPS':['biceps'], 'TRICEPS':['triceps'],
           'FOREARMS':['forearms'], 'QUADS':['quadriceps'], 'HAMSTRINGS':['hamstrings'],
           'GLUTES':['glutes'], 'CALVES':['calves'], 'CORE':['abdominals']}

def q(s):
    return json.dumps(s, ensure_ascii=False).replace('$', '\\$')

def muscles(items):
    return 'listOf(' + ', '.join('Muscle.' + v for v in dict.fromkeys(MUSCLES[x] for x in items)) + ')'

groups = {}
group = None
records = []
for line in (ROOT/'tools/exercise_selection.tsv').read_text().splitlines():
    if '|' not in line:
        group = line
        groups[group] = []
        continue
    source_id, name, technique = line.split('|')
    e = DATA[source_id]
    assert set(TARGETS[group]) & set(e['primaryMuscles'] + e['secondaryMuscles']), source_id
    r = dict(e, appId=ALIASES.get(source_id, 'fedb_' + source_id), appGroup=group,
             nameRu=name, techniqueRu=technique, videoUrl=VIDEOS.get(source_id, {}).get('videoUrl'))
    groups[group].append(r)
    records.append(r)
assert all(len(v) == 20 for v in groups.values())
assert len({r['id'] for r in records}) == 220
assert len({r['appId'] for r in records}) == 220
assets = ROOT/'app/src/main/assets/free_exercise_db'
if assets.exists():
    shutil.rmtree(assets)
assets.mkdir(parents=True, exist_ok=True)
extra_media = []
for app_id, item in json.loads((ROOT/'tools/exercise_extra_media.json').read_text()).items():
    extra_media.append(dict(DATA[item['sourceId']], appId=app_id,
        photoNote=item.get('photoNote'), videoUrl=VIDEOS.get(item['sourceId'], {}).get('videoUrl')))
media_records = records + extra_media
for r in media_records:
    assert len(r['images']) >= 2, r['id']
    for image in r['images']:
        dest = assets/image
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(SOURCE/'exercises'/image, dest)
(assets/'additional_media.json').write_text(json.dumps(extra_media, ensure_ascii=False, indent=2))
(assets/'catalog.json').write_text(json.dumps(records, ensure_ascii=False, indent=2))
(assets/'photos.sha256').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+' '+str(p.relative_to(assets))+'\n' for p in sorted(assets.rglob('*.jpg'))))

out = ['package com.fitclub.tracker.data.seed', '',
       'import com.fitclub.tracker.data.db.ExerciseEntity',
       'import com.fitclub.tracker.data.model.*', '',
       '// Generated by tools/import_free_exercise_db.py; edit the reviewed TSV, not this file.',
       'data class ExerciseMedia(val sourceId: String, val images: List<String>, val category: String, val videoUrl: String?, val photoNote: String? = null)',
       '', 'object FreeExerciseCatalog {',
       '    val all: List<ExerciseEntity> by lazy { ' + ' + '.join('group'+g+'()' for g in groups) + ' }',
       '    val media: Map<String, ExerciseMedia> by lazy { mapOf(']
for r in media_records:
    out.append('        '+q(r['appId'])+' to ExerciseMedia('+q(r['id'])+', listOf('+', '.join(q('free_exercise_db/'+x) for x in r['images'])+'), '+q(CATEGORIES[r['category']])+', '+(q(r['videoUrl']) if r['videoUrl'] else 'null')+', '+(q(r['photoNote']) if r.get('photoNote') else 'null')+'),')
out += ['    ) }']
for g, rows in groups.items():
    out.append('    private fun group'+g+'(): List<ExerciseEntity> = listOf(')
    for r in rows:
        p = r['primaryMuscles']
        s = r['secondaryMuscles']
        secondary_target = not set(TARGETS[g]) & set(p)
        title = 'Дополнительная нагрузка на группу; основные мышцы указаны ниже.' if secondary_target else 'Основная нагрузка соответствует указанным мышцам.'
        impact = CATEGORIES[r['category']] + '. ' + title
        out += ['        ExerciseEntity(',
                '            id = '+q(r['appId'])+', name = '+q(r['nameRu'])+', group = MuscleGroup.'+g+',',
                '            primary = '+muscles(p)+', secondary = '+muscles(s)+',',
                '            type = ExerciseType.'+('BASIC' if r['mechanic']=='compound' else 'ISOLATION')+', level = '+str({'beginner':1,'intermediate':2,'expert':3}[r['level']])+',',
                '            equipment = '+q(EQUIPMENT.get(r['equipment'],'Дополнительная опора'))+',',
                '            technique = '+q(r['techniqueRu'])+', impact = '+q(impact)+', alternatives = emptyList()',
                '        ),']
    out += ['    )']
out += ['}', '']
(ROOT/'app/src/main/java/com/fitclub/tracker/data/seed/FreeExerciseCatalog.kt').write_text('\n'.join(out))
(ROOT/'docs/FREE_EXERCISE_DB_IMPORT.json').write_text(json.dumps({
    'source':'https://github.com/yuhonas/free-exercise-db',
    'datasetSha256':hashlib.sha256((SOURCE/'dist/exercises.json').read_bytes()).hexdigest(),
    'sourceRecords':len(DATA), 'importedRecords':len(records),
    'counts':{g:len(v) for g,v in groups.items()},
    'primaryTargetCounts':{g:sum(bool(set(TARGETS[g]) & set(r['primaryMuscles'])) for r in v) for g,v in groups.items()},
    'photos':len(list(assets.rglob('*.jpg'))),
    'exercisesWithMedia':len(media_records),
    'videoLinks':sum(bool(r['videoUrl']) for r in records),
    'videoSource':'https://github.com/yuhonas/free-exercise-db/pull/26',
    'videoSourceCommit':'fe40028def68793fbd5c652402420a4c63d28a1e',
    'notes':['Photos bundled unchanged; videos are YouTube links, not downloaded files.',
             '5 calf stretches are tagged as stretching.', '6 glute entries target glutes as a secondary muscle.',
             'Russian technique is an abridged adaptation; original instructions retained in catalog.json.']
}, ensure_ascii=False, indent=2))
print('Imported', len(records), 'records;', len(list(assets.rglob('*.jpg'))), 'unique photos;', len(media_records), 'exercises with photos')
