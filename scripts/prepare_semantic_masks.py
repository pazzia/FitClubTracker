"""Separate athlete and active apparatus before pixel segmentation; never retain the gym as one object."""
import argparse, hashlib, io, json, urllib.request, zipfile
from pathlib import Path
import cv2
import numpy as np
from PIL import Image
import torch
from transformers import AutoProcessor, AutoModelForZeroShotObjectDetection
from huggingface_hub import model_info
from mobile_sam import sam_model_registry, SamPredictor

SOURCE='f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5'
SAM_REV='f706ad9c4eb7f219c00d9050e46328518ffb65d2'
DINO_REV='a2bb814dd30d776dcf7e30523b00659f4f141c71'
PROBE={'Barbell_Bench_Press_-_Medium_Grip','Incline_Dumbbell_Press','Dumbbell_Flyes','Close-Grip_Barbell_Bench_Press','Barbell_Curl','Leg_Press'}

def download(url,target):
 if not target.exists():
  target.parent.mkdir(parents=True,exist_ok=True)
  with urllib.request.urlopen(url,timeout=120) as response, target.with_suffix('.download').open('wb') as out:
   while chunk:=response.read(1024*1024):out.write(chunk)
  target.with_suffix('.download').replace(target)

def iou(a,b):
 x1,y1=np.maximum(a[:2],b[:2]);x2,y2=np.minimum(a[2:],b[2:]);intersection=max(0,x2-x1)*max(0,y2-y1)
 return intersection/max(1,(a[2]-a[0])*(a[3]-a[1])+(b[2]-b[0])*(b[3]-b[1])-intersection)

def prompts(record):
 eq=record.get('equipment') or 'body only';name=record['id'].lower()
 groups={}
 if eq=='dumbbell':groups['dumbbell']=2
 elif eq=='barbell' or eq=='e-z curl bar':groups['barbell']=1
 elif eq=='kettlebells':groups['kettlebell']=2
 elif eq in ('machine','cable'):groups['exercise machine']=2 if 'crossover' in name else 1
 elif eq=='exercise ball':groups['exercise ball']=1
 elif eq=='bands':groups['resistance band']=2
 elif eq=='other':groups['weight plate']=2
 if any(part in name for part in ['pull-up','pullup','pull_up','chin-up','chin_up']):groups['pull-up bar']=1
 if 'dip' in name:groups['parallel bars']=1
 if eq not in ('machine','cable') and any(part in name for part in ['bench','incline','decline','lying','seated','flyes','concentration','preacher','bulgarian','step-up']):groups['exercise bench']=1
 if eq=='body only' and any(part in name for part in ['crunch','plank','roller','push-up','push_up','bridge','glute','hamstring','dead_bug','air_bike','russian']):groups['exercise mat']=1
 return groups

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--shard',type=int,default=0);parser.add_argument('--shards',type=int,default=1);parser.add_argument('--probe',action='store_true');args=parser.parse_args()
 root=Path(__file__).resolve().parents[1];cache=root/'.gradle/semantic-contours';output=root/'app/src/main/assets/exercise_masks'
 torch.set_num_threads(2)
 revision=model_info('IDEA-Research/grounding-dino-tiny',revision=DINO_REV).sha
 processor=AutoProcessor.from_pretrained('IDEA-Research/grounding-dino-tiny',revision=revision)
 processor.image_processor.size={'shortest_edge':640,'longest_edge':960}
 detector=AutoModelForZeroShotObjectDetection.from_pretrained('IDEA-Research/grounding-dino-tiny',revision=revision).eval()
 detector.config.disable_custom_kernels=True
 checkpoint=cache/'mobile_sam.pt';download(f'https://raw.githubusercontent.com/ChaoningZhang/MobileSAM/{SAM_REV}/weights/mobile_sam.pt',checkpoint)
 sam=sam_model_registry['vit_t'](checkpoint=str(checkpoint)).eval();predictor=SamPredictor(sam)
 archive=cache/f'{SOURCE}.zip';download(f'https://codeload.github.com/yuhonas/free-exercise-db/zip/{SOURCE}',archive)
 wanted=[line.split(' ',1) for line in (root/'app/src/main/assets/free_exercise_db/photos.sha256').read_text().splitlines() if line.strip()]
 selected=[(sha,p) for sha,p in wanted if not args.probe or p.split('/')[0] in PROBE][args.shard::args.shards]
 report={'dino_revision':revision,'sam_revision':SAM_REV,'sam_sha256':hashlib.sha256(checkpoint.read_bytes()).hexdigest(),'photos':[]}
 with zipfile.ZipFile(archive) as z:
  records={r['id']:r for r in json.loads(z.read(f'free-exercise-db-{SOURCE}/dist/exercises.json'))}
  for index,(expected,path) in enumerate(selected):
   data=z.read(f'free-exercise-db-{SOURCE}/exercises/{path}');assert hashlib.sha256(data).hexdigest()==expected
   image=Image.open(io.BytesIO(data)).convert('RGB');array=np.asarray(image);w,h=image.size;groups=prompts(records[path.split('/')[0]])
   text='a person. '+''.join('a '+label+'. ' for label in groups)
   inputs=processor(images=image,text=text,return_tensors='pt')
   with torch.inference_mode():result=detector(**inputs)
   found=processor.post_process_grounded_object_detection(result,inputs.input_ids,box_threshold=.10,text_threshold=.10,target_sizes=[(h,w)])[0]
   candidates=[]
   for box,label,score in zip(found['boxes'].numpy(),found['labels'],found['scores'].numpy()):
    label=label.removeprefix('a ').strip();box=np.clip(box,[0,0,0,0],[w-1,h-1,w-1,h-1])
    if not any(old['label']==label and iou(old['box'],box)>.7 for old in candidates):candidates.append({'box':box,'label':label,'score':float(score)})
   people=[r for r in candidates if 'person' in r['label']]
   assert people,f'No athlete detected: {path}'
   athlete=max(people,key=lambda r:r['score']*np.sqrt((r['box'][2]-r['box'][0])*(r['box'][3]-r['box'][1])/(w*h)))
   predictor.set_image(array)
   prior_path=output/Path(path).with_suffix('.png')
   prior=np.asarray(Image.open(prior_path).convert('L'),dtype=np.float32)/255
   def segment(box, max_coverage=.98):
    # Never use salient-background pixels as negative prompts: they can be part
    # of the active machine. Segment whole objects independently of old mattes.
    masks,scores,_=predictor.predict(box=box,multimask_output=True)
    valid=[i for i,mask in enumerate(masks) if .003 < mask.mean() < max_coverage]
    return masks[max(valid,key=lambda i:float(scores[i]))] if valid else None

   person=segment(athlete['box']);assert person is not None,f'No athlete pixels: {path}'
   radius=max(9,round(max(w,h)*.025));near=cv2.dilate(person.astype('uint8'),cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(2*radius+1,2*radius+1)))>0
   foreground=person.copy();kept=[]
   for label,maximum in groups.items():
    objects=[]
    possible=sorted([r for r in candidates if label in r['label']],key=lambda r:r['score'],reverse=True)[:8]
    for r in possible:
     # Broad, low-confidence mat detections are often the surrounding floor.
     if label=='exercise mat':
      box=r['box'];area=(box[2]-box[0])*(box[3]-box[1])/(w*h)
      if r['score']<.25 or area>.65:continue
     mask=segment(r['box'],max_coverage=.65 if label=='exercise mat' else .98)
     if mask is None:continue
     # Active gear touches the athlete. Objects elsewhere in the gym are rejected.
     contact=int((mask & near).sum());fraction=contact/max(1,int(mask.sum()))
     if contact>12 and fraction>.004:objects.append((r['score'] if label=='exercise mat' else contact*max(.2,r['score']),r,mask))
    for _,r,mask in sorted(objects,key=lambda row:row[0],reverse=True)[:maximum]:
     if (foreground | mask).mean()>=.98:continue
     foreground|=mask;kept.append({'label':label,'box':r['box'].tolist(),'score':r['score']})
   target=output/Path(path).with_suffix('.png');target.parent.mkdir(parents=True,exist_ok=True)
   # Feather only the subpixel boundary; interiors keep the original photo unchanged.
   from PIL import ImageFilter
   contour=Image.fromarray((foreground*255).astype('uint8')).filter(ImageFilter.GaussianBlur(.6))
   contour.convert('RGB').save(target,optimize=True)
   report['photos'].append({'path':path,'source_sha256':expected,'mask_sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'coverage':float(foreground.mean()),'equipment':records[path.split('/')[0]].get('equipment'),'kept':kept,'detections':[{'label':r['label'],'score':r['score'],'box':r['box'].tolist()} for r in candidates]})
   print(f'Semantic {args.shard}: {index+1}/{len(selected)} {path} athlete+{[r["label"] for r in kept]}',flush=True)
   del inputs,result,found,data,image,array,prior,person,foreground
 root.joinpath('contour-reports').mkdir(exist_ok=True);(root/'contour-reports'/f'semantic-{args.shard}.json').write_text(json.dumps(report,indent=2))
 print('DINO_REVISION='+revision,flush=True)

if __name__=='__main__':main()
