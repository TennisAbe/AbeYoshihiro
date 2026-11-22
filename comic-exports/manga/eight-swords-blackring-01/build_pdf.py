from pathlib import Path
import hashlib,json
from PIL import Image
from reportlab.pdfgen import canvas
from pypdf import PdfReader

ROOT=Path(__file__).resolve().parent
records=[]
for n in range(1,31):
    p=ROOT/f'page-{n:02}.png'
    with Image.open(p) as im: im.verify()
    records.append({'page':n,'image':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})

def build(name, rows):
    out=ROOT/name
    c=canvas.Canvas(str(out),pagesize=(595.276,841.89),pageCompression=1)
    c.setTitle('NOCTALITH - Black Ring Fortress')
    c.setViewerPreference('Direction','R2L')
    for row in rows:
        p=ROOT/row['image']
        with Image.open(p) as im:w,h=im.size
        scale=min(595.276/w,841.89/h)
        c.drawImage(str(p),(595.276-w*scale)/2,(841.89-h*scale)/2,width=w*scale,height=h*scale)
        c.showPage()
    c.save()
    assert len(PdfReader(out).pages)==len(rows)
    return out

out=build('NOCTALITH-blackring-30pages.pdf',records)
outputs=[out]
if out.stat().st_size>=99_000_000:
    outputs=[build('NOCTALITH-blackring-part1-pages01-15.pdf',records[:15]),build('NOCTALITH-blackring-part2-pages16-30.pdf',records[15:])]
    out.unlink()
assert all(p.stat().st_size<100_000_000 for p in outputs)
(ROOT/'pages.json').write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'pages':30,'outputs':[{ 'file':p.name,'bytes':p.stat().st_size} for p in outputs]}))
