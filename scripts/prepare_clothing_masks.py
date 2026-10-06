"""Prepare garment-only mattes. Original exercise photographs are never edited.

The Android renderer uses these mattes only for list thumbnails. Parsing excludes
skin, hair, footwear and equipment; uncertain boundary pixels are left alone.
"""
import argparse
import hashlib
import io
import json
import urllib.request
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image
import torch
from transformers import SegformerImageProcessor, SegformerForSemanticSegmentation

SOURCE = 'f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5'
MODEL = 'mattmdjaga/segformer_b2_clothes'
REVISION = '45c27abae29700890f00296e7b652186239b30df'

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--shard', type=int, default=0)
    parser.add_argument('--shards', type=int, default=1)
    args = parser.parse_args()
    assert 0 <= args.shard < args.shards
    root = Path(__file__).resolve().parents[1]
    assets = root / 'app/src/main/assets'
    wanted = [line.split(' ', 1) for line in
              (assets / 'free_exercise_db/photos.sha256').read_text().splitlines()
              if line.strip() and line.endswith('/1.jpg')]
    assert len(wanted) == 224
    torch.set_num_threads(2)
    processor = SegformerImageProcessor(size={'height': 512, 'width': 512})
    model = SegformerForSemanticSegmentation.from_pretrained(MODEL, revision=REVISION).eval()
    assert model.config.id2label[4] == 'Upper-clothes'
    cache = root / '.gradle/clothing'
    cache.mkdir(parents=True, exist_ok=True)
    archive = cache / f'{SOURCE}.zip'
    if not archive.exists():
        with urllib.request.urlopen(f'https://codeload.github.com/yuhonas/free-exercise-db/zip/{SOURCE}', timeout=120) as response:
            archive.write_bytes(response.read())
    rows = []
    with zipfile.ZipFile(archive) as zip_file:
        for expected, path in wanted[args.shard::args.shards]:
            data = zip_file.read(f'free-exercise-db-{SOURCE}/exercises/{path}')
            assert hashlib.sha256(data).hexdigest() == expected
            image = Image.open(io.BytesIO(data)).convert('RGB')
            w, h = image.size
            inputs = processor(images=image, return_tensors='pt')
            with torch.inference_mode():
                logits = model(**inputs).logits
                probabilities = torch.nn.functional.interpolate(logits, size=(h, w), mode='bilinear', align_corners=False).softmax(dim=1)[0]
                labels = probabilities.argmax(dim=0)
                # Hard, conservative class selection avoids feathering into exposed skin.
                selected = ((labels == 4) & (probabilities[4] >= .75)).numpy()
            foreground_path = assets / 'exercise_masks' / Path(path).with_suffix('.png')
            foreground = np.asarray(Image.open(foreground_path).convert('L')) >= 240
            assert foreground.shape == selected.shape
            selected &= foreground
            # Keep the largest garment component: reject people reflected in mirrors.
            from scipy.ndimage import label, binary_erosion
            components, count = label(selected)
            if count:
                sizes = np.bincount(components.ravel()); sizes[0] = 0
                selected = binary_erosion(components == sizes.argmax(), iterations=1)
            target = assets / 'exercise_clothing' / Path(path).with_suffix('.png')
            target.parent.mkdir(parents=True, exist_ok=True)
            Image.fromarray((selected * 255).astype('uint8')).convert('RGB').save(target, optimize=True)
            rows.append({'path': path, 'source_sha256': expected,
                         'mask_sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                         'width': w, 'height': h, 'clothing_pixels': int(selected.sum())})
            print(f'Clothing {args.shard}: {path}: {int(selected.sum())} pixels', flush=True)
    report = assets / 'exercise_clothing' / f'shard-{args.shard}.json'
    report.write_text(json.dumps({'model': MODEL, 'revision': REVISION, 'photos': rows}, indent=2))

if __name__ == '__main__':
    main()
