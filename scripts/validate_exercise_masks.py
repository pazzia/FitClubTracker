"""Verify source and released contour asset hashes."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
assets = root / 'app/src/main/assets'
sources = dict(line.split(' ', 1)[::-1] for line in (assets / 'free_exercise_db/photos.sha256').read_text().splitlines() if line.strip())
manifest = json.loads((assets / 'exercise_masks/manifest.json').read_text())
entries = manifest['photos']
assert len(sources) == len(entries) == 448
assert len({row['path'] for row in entries}) == 448
assert {row['path'] for row in entries} == set(sources)
for row in entries:
    assert row['source_sha256'] == sources[row['path']], row['path']
    mask = assets / 'exercise_masks' / Path(row['path']).with_suffix('.png')
    assert hashlib.sha256(mask.read_bytes()).hexdigest() == row['mask_sha256'], str(mask)
print('Verified all 448 released contour hashes and source photographs')
