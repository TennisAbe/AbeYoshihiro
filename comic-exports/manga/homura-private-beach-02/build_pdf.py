"""Assemble original PNG artwork without modifying its pixels."""
from pathlib import Path
import hashlib
import json
from PIL import Image
from reportlab.pdfgen import canvas
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parent
PDF = ROOT / 'NOCTALITH-homura-private-beach-20pages.pdf'
W, H = 595.276, 841.89
c = canvas.Canvas(str(PDF), pagesize=(W, H), pageCompression=1)
c.setTitle('NOCTALITH - Futaridake no umi')
c.setAuthor('NOCTALITH')
c.setViewerPreference('Direction', 'R2L')
records = []
for page in range(1, 21):
    spread = page in (3, 4)
    filename = 'spread-03-04.png' if spread else f'page-{page:02d}.png'
    image = ROOT / filename
    with Image.open(image) as im:
        im.verify()
    with Image.open(image) as im:
        iw, ih = im.size
    scale = min(W / (iw / 2 if spread else iw), H / ih)
    dw, dh = iw * scale, ih * scale
    y = (H - dh) / 2
    if spread:
        half = dw / 2
        x = (W - half) / 2 - (half if page == 3 else 0)
        c.saveState()
        clip = c.beginPath()
        clip.rect(0, 0, W, H)
        c.clipPath(clip, stroke=0, fill=0)
        c.drawImage(str(image), x, y, width=dw, height=dh)
        c.restoreState()
    else:
        c.drawImage(str(image), (W - dw) / 2, y, width=dw, height=dh)
    c.showPage()
    records.append({'page': page, 'image': filename,
                    'region': ('right-half' if page == 3 else 'left-half') if spread else 'whole',
                    'dimensions': [iw, ih],
                    'sha256': hashlib.sha256(image.read_bytes()).hexdigest()})
c.save()
assert len(PdfReader(PDF).pages) == 20
assert PDF.stat().st_size < 100_000_000
(ROOT / 'pages.json').write_text(json.dumps(records, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'pdf': PDF.name, 'pages': 20, 'artwork_files': len(list(ROOT.glob('*.png'))), 'bytes': PDF.stat().st_size}))
