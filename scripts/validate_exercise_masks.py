"""Verify source and released contour asset hashes."""
import hashlib
import json
from pathlib import Path
from PIL import Image

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
    with Image.open(mask) as image, Image.open(root / 'app/build/generated/exercisePhotos/free_exercise_db' / row['path']) as source:
        assert image.size == source.size, f"Mask dimensions: {row['path']}"
        histogram = image.convert('RGB').getchannel('R').histogram()
        total = sum(histogram)
        assert sum(histogram[129:]) / total > .003, f"No athlete: {row['path']}"
        assert sum(histogram[:128]) / total > .02, f"No background: {row['path']}"
print('Verified all 448 released contour hashes and source photographs')
