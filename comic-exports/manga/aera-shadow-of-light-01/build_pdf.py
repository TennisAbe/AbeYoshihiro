"""Embed original generated PNG pages in reading order, without changing their pixels."""
from pathlib import Path
import hashlib,json
from PIL import Image
from reportlab.pdfgen import canvas
from reportlab import rl_config
rl_config.useA85 = False
from pypdf import PdfReader
ROOT=Path(__file__).resolve().parent
W,H=595.276,892.914
records=[]
for n in range(1,51):
 p=ROOT/f'page-{n:02d}.png'
 with Image.open(p) as im: im.verify()
 with Image.open(p) as im: size=im.size
 assert size[0]>0 and size[1]>size[0]
 records.append({'page':n,'file':p.name,'width':size[0],'height':size[1],'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
outputs=[]
for start,end,label in [(1,25,'part-1-pages-01-25'),(26,50,'part-2-pages-26-50')]:
 filename=f'NOCTALITH-aera-shadow-of-light-{label}.pdf'
 path=ROOT/filename
 pdf=canvas.Canvas(str(path),pagesize=(W,H),pageCompression=1)
 pdf.setTitle(f'NOCTALITH - Hikari wo ou kage - {start}-{end}')
 pdf.setAuthor('NOCTALITH')
 pdf.setViewerPreference('Direction','R2L')
 for r in records[start-1:end]:
  scale=min(W/r['width'],H/r['height']);dw=r['width']*scale;dh=r['height']*scale
  pdf.drawImage(str(ROOT/r['file']),(W-dw)/2,(H-dh)/2,width=dw,height=dh)
  pdf.showPage()
 pdf.save()
 reader=PdfReader(path)
 assert len(reader.pages)==end-start+1
 assert path.stat().st_size<100_000_000,(filename,path.stat().st_size)
 outputs.append({'file':filename,'start':start,'end':end,'pages':len(reader.pages),'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
(ROOT/'pages.json').write_text(json.dumps({'title':'光を追う影','reading_direction':'right-to-left','pages':records,'pdfs':outputs},ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'artwork_pages':len(records),'pdfs':outputs},ensure_ascii=False))
