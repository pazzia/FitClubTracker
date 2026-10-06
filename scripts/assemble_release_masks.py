"""Assemble only regenerated images owned by each shard; validate every byte before publishing."""
import hashlib
import json
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
assets = root / 'app/src/main/assets'
parts = root / '.gradle/prepared-contour-shards'
output = assets / 'exercise_masks'
expected = dict(line.split(' ', 1)[::-1] for line in (assets / 'free_exercise_db/photos.sha256').read_text().splitlines() if line.strip())
rows = []
revisions = set()
for shard in range(8):
    report = json.loads((parts / 'reports' / f'contour-report-{shard}' / f'semantic-{shard}.json').read_text())
    revisions.add((report['dino_revision'], report['sam_revision'], report['sam_sha256']))
    for row in report['photos']:
        path = Path(row['path']).with_suffix('.png')
        source = parts / 'masks' / f'exercise-contours-{shard}' / path
        assert row['source_sha256'] == expected[row['path']], row['path']
        assert hashlib.sha256(source.read_bytes()).hexdigest() == row['mask_sha256'], str(source)
        target = output / path
        target.parent.mkdir(parents=True, exist_ok=True)
        with Image.open(source) as mask:
            mask.load()
            mask.convert('RGB').save(target, optimize=True)
        rows.append({'path': row['path'], 'source_sha256': row['source_sha256'],
                     'mask_sha256': hashlib.sha256(target.read_bytes()).hexdigest()})
assert len(rows) == len({r['path'] for r in rows}) == 448
assert {r['path'] for r in rows} == set(expected)
assert len(revisions) == 1
dino, sam, checkpoint = revisions.pop()
manifest = {'source_revision': 'f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5',
            'dino_revision': dino, 'sam_revision': sam, 'sam_sha256': checkpoint,
            'photos': sorted(rows, key=lambda row: row['path'])}
(output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print('Assembled 448 individually verified contour masks')
