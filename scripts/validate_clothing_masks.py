import hashlib,json
from pathlib import Path
from PIL import Image
root=Path(__file__).resolve().parents[1]
assets=root/'app/src/main/assets'
expected={p:sha for sha,p in (line.split(' ',1) for line in (assets/'free_exercise_db/photos.sha256').read_text().splitlines() if line.strip()) if p.endswith('/1.jpg')}
rows=[]
for shard in sorted((assets/'exercise_clothing').glob('shard-*.json')):
    data=json.loads(shard.read_text())
    assert data['revision']=='45c27abae29700890f00296e7b652186239b30df'
    rows+=data['photos']
assert len(rows)==len(expected)==224
assert len({r['path'] for r in rows})==224
assert {r['path'] for r in rows}==set(expected)
for r in rows:
    assert r['source_sha256']==expected[r['path']]
    path=assets/'exercise_clothing'/Path(r['path']).with_suffix('.png')
    assert hashlib.sha256(path.read_bytes()).hexdigest()==r['mask_sha256']
    with Image.open(path) as mask,Image.open(assets/'exercise_masks'/Path(r['path']).with_suffix('.png')) as fg:
        assert mask.size==fg.size==(r['width'],r['height'])
print('Verified clothing mask coverage and hashes for all 224 thumbnails')
