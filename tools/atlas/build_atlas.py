#!/usr/bin/env python3
"""Build app/src/main/assets/atlas from the verified fitness-MVP catalog.

Usage:
  python3 tools/atlas/build_atlas.py fitness-mvp-verified.html exact-schematics.json

Inputs come from the reviewed archive (fitclub-mvp-checkpoint.zip):
  * fitness-mvp-verified.html — 120 verified cards with embedded WebP illustrations;
  * audit/exact-schematics.json — origin/insertion rows for the 9 cards whose disputed
    illustration was replaced by a schematic (rendered natively by the app, not as a picture).

Exercise links and training notes are kept from the current atlas.json, matched by Russian name.
"""
import base64
import io
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/main/assets/atlas"
GROUPS = [("Грудь", "chest"), ("Плечи", "shoulders"), ("Спина", "back"), ("Плечо и предплечье", "arm"),
          ("Кисть и хват", "hand"), ("Кор", "core"), ("Таз и ягодицы", "hips"), ("Бедро", "thigh"),
          ("Голень", "leg"), ("Стопа", "foot"), ("Шея", "neck"), ("Дыхание", "breathing")]


# The source keeps an inverted word order for some names ("Flexor radialis carpi").
# The app shows the Terminologia Anatomica order ("Flexor carpi radialis").
LATIN = {
    "Rotatores thoracis": "Musculi rotatores thoracis", "Flexor radialis carpi": "Flexor carpi radialis",
    "Flexor ulnaris carpi": "Flexor carpi ulnaris", "Extensor radialis longus carpi": "Extensor carpi radialis longus",
    "Extensor radialis brevis carpi": "Extensor carpi radialis brevis", "Extensor ulnaris carpi": "Extensor carpi ulnaris",
    "Flexor superficialis digitorum": "Flexor digitorum superficialis", "Flexor profundus digitorum": "Flexor digitorum profundus",
    "Flexor longus pollicis": "Flexor pollicis longus", "Extensor longus pollicis": "Extensor pollicis longus",
    "Extensor brevis pollicis": "Extensor pollicis brevis", "Abductor longus pollicis": "Abductor pollicis longus",
    "Abductor brevis pollicis": "Abductor pollicis brevis", "Flexor brevis pollicis": "Flexor pollicis brevis",
    "Flexor digiti minimi manus": "Flexor digiti minimi brevis manus", "Extensor longus digitorum": "Extensor digitorum longus",
    "Extensor longus hallucis": "Extensor hallucis longus", "Flexor longus digitorum": "Flexor digitorum longus",
    "Flexor longus hallucis": "Flexor hallucis longus", "Extensor brevis hallucis": "Extensor hallucis brevis",
    "Extensor brevis digitorum": "Extensor digitorum brevis", "Flexor brevis hallucis": "Flexor hallucis brevis",
    "Flexor digiti minimi pedis": "Flexor digiti minimi brevis pedis", "Flexor brevis digitorum": "Flexor digitorum brevis",
    "Musculus rectus anterior capitis": "Musculus rectus capitis anterior",
    "Musculus rectus lateralis capitis": "Musculus rectus capitis lateralis", "DIAPHRAGMA": "Diaphragma",
}


def latin(name: str) -> str:
    return LATIN.get(name, name)


def norm(s: str) -> str:
    return s.lower().replace("ё", "е").strip()


def thumbnail(im: Image.Image) -> Image.Image:
    """Square crop around the terracotta-highlighted muscle, ignoring the title and legend."""
    a = np.asarray(im).astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mask = (r - g > 35) & (r - b > 45) & (r > 120)
    h, w = mask.shape
    mask[: int(h * 0.215), :] = False
    mask[int(h * 0.86):, :] = False
    ys, xs = np.nonzero(mask)
    if len(xs) > 200:
        x0, x1 = np.percentile(xs, [6, 94])
        y0, y1 = np.percentile(ys, [6, 94])
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        side = min(max(max(x1 - x0, y1 - y0) * 1.15, 260), min(w, h))
    else:
        cx, cy, side = w / 2, h * 0.55, w * 0.6
    left = int(min(max(cx - side / 2, 0), w - side))
    top = int(min(max(cy - side / 2, 0), h - side))
    return im.crop((left, top, left + int(side), top + int(side))).resize((240, 240), Image.LANCZOS)


def main() -> None:
    html, schematics_path = Path(sys.argv[1]), Path(sys.argv[2])
    text = html.read_text()
    start = text.index("const DATA=") + len("const DATA=")
    data, _ = json.JSONDecoder().raw_decode(text[start:])
    cards = [c for c in data["cards"] if c["id"].startswith("fit-")]
    assert len(cards) == 120, len(cards)
    schematics = json.loads(schematics_path.read_text())

    previous = json.loads((OUT / "atlas.json").read_text())
    old = {norm(m["name"]): m for g in previous["groups"] for m in g["muscles"]}

    for f in OUT.glob("*.webp"):
        f.unlink()

    groups = {gid: {"id": gid, "title": title, "muscles": []} for title, gid in GROUPS}
    by_region = {title: gid for title, gid in GROUPS}
    for c in cards:
        prior = old.get(norm(c["name_ru"]))
        assert prior is not None, f"No exercise mapping for {c['name_ru']}"
        muscle = {
            "id": c["id"], "name": c["name_ru"], "latin": latin(c["name_la"]),
            "role": c["role"], "level": c["level"],
            "where": c["location_ru"], "does": c["action_ru"], "fitness": c["fitness_ru"],
            "details": c["anatomy_note_ru"],
            "aliases": c.get("aliases", []),
            "parts": [{"name": p["name_ru"], "latin": latin(p["name_la"])} for p in c.get("parts", [])],
            "members": [{"name": m["name_ru"], "latin": latin(m["name_la"])} for m in c.get("members", [])
                        if norm(m["name_ru"]) != norm(c["name_ru"])],
            "trainingNote": prior.get("trainingNote"),
            "exercises": prior.get("exercises", []),
        }
        if c["id"] in schematics:
            muscle["schematic"] = [{"part": row[0], "origin": row[1], "insertion": row[2]} for row in schematics[c["id"]]]
        else:
            blob = base64.b64decode(c["image"].split(",", 1)[1], validate=True)
            im = Image.open(io.BytesIO(blob)).convert("RGB")
            im.resize((640, 960), Image.LANCZOS).save(OUT / f"{c['id']}.webp", quality=72, method=6)
            thumbnail(im).save(OUT / f"{c['id']}_thumb.webp", quality=78, method=6)
        groups[by_region[c["region"]]]["muscles"].append({k: v for k, v in muscle.items() if v not in (None, [], "")})

    atlas = {"version": "mvp-2026-10-11", "source": "Фитнес-MVP, проверка 11.10.2026: 120 карточек",
             "groups": [groups[gid] for _, gid in GROUPS]}
    (OUT / "atlas.json").write_text(json.dumps(atlas, ensure_ascii=False, separators=(",", ":")))
    print(sum(len(g["muscles"]) for g in atlas["groups"]), "muscles,", len(schematics), "schematics")


if __name__ == "__main__":
    main()
