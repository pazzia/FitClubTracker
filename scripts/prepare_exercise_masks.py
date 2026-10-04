"""Build-time contour mattes; originals/poses remain untouched, no inference on phones.
BiRefNet general-lite includes salient objects rather than selfie/person-only masks.
Weights/source and checksums are pinned. Outputs are versioned assets after review.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import urllib.request
import zipfile
import numpy as np
import onnxruntime as ort
from PIL import Image

SOURCE = 'f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5'
MODEL_URL = 'https://github.com/danielgatis/rembg/releases/download/v0.0.0/BiRefNet-general-bb_swin_v1_tiny-epoch_232.onnx'
MODEL_MD5 = '4fab47adc4ff364be1713e97b7e66334'

def download(url, target):
    if not target.exists():
        target.parent.mkdir(parents=True, exist_ok=True)
        temp = target.with_suffix('.download')
        urllib.request.urlretrieve(url, temp)
        temp.replace(target)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--shard', type=int, default=0)
    parser.add_argument('--shards', type=int, default=1)
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]
    cache=root/'.gradle/contours'
    model=cache/'birefnet-general-lite.onnx'
    wanted=[line.split(' ', 1) for line in (root/'app/src/main/assets/free_exercise_db/photos.sha256').read_text().splitlines() if line.strip()]
    assert len(wanted)==448
    output=root/'app/src/main/assets/exercise_masks'
    selected=wanted[args.shard::args.shards]
    if all((output/Path(path).with_suffix('.png')).exists() for sha,path in selected):
        print(f'Shard {args.shard}: {len(selected)} committed masks available', flush=True)
        return
    download(MODEL_URL, model)
    assert hashlib.md5(model.read_bytes()).hexdigest()==MODEL_MD5, 'Model checksum mismatch'
    archive=cache/f'{SOURCE}.zip'
    download(f'https://codeload.github.com/yuhonas/free-exercise-db/zip/{SOURCE}', archive)
    options=ort.SessionOptions()
    options.intra_op_num_threads=2
    options.inter_op_num_threads=1
    session=ort.InferenceSession(str(model), options, providers=['CPUExecutionProvider'])
    mean=np.asarray([.485,.456,.406], dtype=np.float32)
    std=np.asarray([.229,.224,.225], dtype=np.float32)
    summary=[]
    with zipfile.ZipFile(archive) as zip:
        for index,(expected,path) in enumerate(selected):
            target=output/Path(path).with_suffix('.png')
            target.parent.mkdir(parents=True, exist_ok=True)
            data=zip.read(f'free-exercise-db-{SOURCE}/exercises/{path}')
            assert hashlib.sha256(data).hexdigest()==expected, f'Source checksum: {path}'
            import io
            image=Image.open(io.BytesIO(data)).convert('RGB')
            tensor=np.asarray(image.resize((1024,1024), Image.Resampling.LANCZOS), dtype=np.float32)/255
            tensor=((tensor-mean)/std).transpose(2,0,1)[None]
            logits=session.run(None,{session.get_inputs()[0].name:tensor})[0][0,0]
            prediction=1/(1+np.exp(-np.clip(logits,-30,30)))
            low,high=float(prediction.min()),float(prediction.max())
            assert high-low>.05, f'Empty/flat mask: {path}'
            prediction=(prediction-low)/(high-low)
            matte=Image.fromarray((prediction*255).astype('uint8')).resize(image.size,Image.Resampling.LANCZOS)
            matte.save(target, optimize=True)
            coverage=float((np.asarray(matte)>128).mean())
            assert .003<coverage<.98, f'Invalid foreground coverage {coverage}: {path}'
            summary.append({'path':path,'coverage':coverage,'source_sha256':expected,'mask_sha256':hashlib.sha256(target.read_bytes()).hexdigest()})
            print(f'Shard {args.shard}: {index+1}/{len(selected)} {path} foreground={coverage:.3f}',flush=True)
    report=root/'contour-reports'/f'shard-{args.shard}.json'
    report.parent.mkdir(exist_ok=True)
    report.write_text(json.dumps(summary, indent=2))

if __name__=='__main__':
    main()
