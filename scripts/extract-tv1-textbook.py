"""Extract every scanned page and colour illustration, preserving source-page coordinates.

Requires pypdf, Pillow and numpy. The source PDF is read-only. Page images are
retained as the authoritative source, including monochrome drawings and writing.
"""
import argparse
import hashlib
import json
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from pypdf import PdfReader


def illustration_boxes(image):
    width, height = image.size
    small = image.convert('RGB').resize((240, round(240 * height / width)))
    pixels = np.asarray(small, dtype=np.int16)
    mask = ((pixels.max(axis=2) - pixels.min(axis=2)) > 45) & (pixels.max(axis=2) > 90)
    # Do not count the lesson-number badge, printed footer or activity icons as illustrations.
    mask[:round(mask.shape[0] * .09), :round(mask.shape[1] * .24)] = False
    mask[round(mask.shape[0] * .96):, :] = False
    grown = np.asarray(Image.fromarray((mask * 255).astype('uint8')).filter(ImageFilter.MaxFilter(9))) > 0
    seen = np.zeros(grown.shape, dtype=bool)
    boxes = []
    rows, cols = grown.shape
    for yy in range(rows):
        for xx in range(cols):
            if seen[yy, xx] or not grown[yy, xx]:
                continue
            seen[yy, xx] = True
            queue = deque([(xx, yy)])
            x0 = x1 = xx
            y0 = y1 = yy
            count = 0
            while queue:
                x, y = queue.popleft()
                count += 1
                x0, x1 = min(x0, x), max(x1, x)
                y0, y1 = min(y0, y), max(y1, y)
                for nx, ny in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)):
                    if 0 <= nx < cols and 0 <= ny < rows and not seen[ny,nx] and grown[ny,nx]:
                        seen[ny,nx] = True
                        queue.append((nx,ny))
            if count < rows * cols * .004 or x1-x0 < 18 or y1-y0 < 16:
                continue
            box = [max(0, round(x0/cols*width)-14), max(0,round(y0/rows*height)-14),
                   min(width,round((x1+1)/cols*width)+14), min(height,round((y1+1)/rows*height)+14)]
            if (box[2]-box[0])*(box[3]-box[1]) < width*height*.008:
                continue
            boxes.append(box)
    # Coloured areas of one drawing may be disconnected (skin, clothes, shoes).
    # Merge touching/nearby boxes, including nested panels, before cutting them.
    gap=18
    changed=True
    while changed:
        changed=False
        merged=[]
        while boxes:
            box=boxes.pop(0)
            index=0
            while index<len(boxes):
                other=boxes[index]
                if box[0]<=other[2]+gap and other[0]<=box[2]+gap and box[1]<=other[3]+gap and other[1]<=box[3]+gap:
                    box=[min(box[0],other[0]),min(box[1],other[1]),max(box[2],other[2]),max(box[3],other[3])]
                    boxes.pop(index)
                    changed=True
                    index=0
                else:
                    index+=1
            merged.append(box)
        boxes=merged
    clean=[]
    native=np.asarray(image.convert('RGB'))
    for box in boxes:
        if box[2]-box[0]<120 or box[3]-box[1]<120:
            continue
        # Include black outlines/text attached to a coloured region instead of cutting them at its edge.
        for _ in range(18):
            x0,y0,x1,y1=box
            crop=native[y0:y1,x0:x1,:]
            ink=(crop.min(axis=2)<190)
            expand=[x0>0 and ink[:,0:2].sum()>3,
                    y0>0 and ink[0:2,:].sum()>3,
                    x1<width and ink[:,-2:].sum()>3,
                    y1<height and ink[-2:,:].sum()>3]
            if not any(expand):
                break
            box=[max(0,x0-10 if expand[0] else x0),max(0,y0-10 if expand[1] else y0),
                 min(width,x1+10 if expand[2] else x1),min(height,y1+10 if expand[3] else y1)]
        else:
            # A crowded table cannot be safely isolated by colour; its complete source page is retained.
            continue
        if box not in clean:
            clean.append(box)
    return sorted(clean,key=lambda b:(b[1]//40,b[0]))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('pdf', type=Path)
    parser.add_argument('--qa-dir', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    media = root / 'src/main/resources/static/media/tv1-tap1'
    data = root / 'src/main/resources/learning/tv1-tap1-assets.json'
    for folder in (media/'pages', media/'illustrations', args.qa_dir):
        folder.mkdir(parents=True,exist_ok=True)
    reader = PdfReader(args.pdf)
    assert len(reader.pages) == 173, 'Source PDF differs from the reviewed 173-page edition'
    # Only remove this script's previously generated crops, inside this exact output folder.
    crop_root=(media/'illustrations').resolve()
    for previous in crop_root.glob('page-*-figure-*.jpg'):
        assert previous.resolve().parent==crop_root
        previous.unlink()
    result = {'sourceName':args.pdf.name,'sourceSha256':hashlib.sha256(args.pdf.read_bytes()).hexdigest(),
              'pageCount':len(reader.pages),'pages':[]}
    for number,page in enumerate(reader.pages,1):
        assert len(page.images)==1, f'Expected one scanned image on PDF page {number}'
        embedded=page.images[0]
        image=embedded.image.convert('RGB')
        filename=f'pages/page-{number:03}.jpg'
        if embedded.name.lower().endswith(('.jpg','.jpeg')):
            (media/filename).write_bytes(embedded.data)
        else:
            image.save(media/filename,quality=96,subsampling=0)
        record={'pdfPage':number,'printedPage':number-1 if number>=3 else None,
                'width':image.width,'height':image.height,'image':f'/media/tv1-tap1/{filename}','illustrations':[]}
        if 5<=number<=170:
            for index,box in enumerate(illustration_boxes(image),1):
                crop=f'illustrations/page-{number:03}-figure-{index:02}.jpg'
                image.crop(box).save(media/crop,quality=96,subsampling=0)
                record['illustrations'].append({'image':f'/media/tv1-tap1/{crop}','bbox':box})
        result['pages'].append(record)
    data.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    # Contact sheets retain all pages, so missing pages can be checked visually.
    for start in range(0,173,20):
        group=result['pages'][start:start+20]
        sheet=Image.new('RGB',(1200,4*385),'#ddd')
        draw=ImageDraw.Draw(sheet)
        for index,record in enumerate(group):
            image=Image.open(media/record['image'].split('/media/tv1-tap1/')[1]).convert('RGB')
            image.thumbnail((230,345))
            x,y=(index%5)*240,(index//5)*385
            sheet.paste(image,(x,y))
            draw.text((x+8,y+350),f"PDF {record['pdfPage']} / book {record['printedPage']}",fill='black')
        sheet.save(args.qa_dir/f'pages-{start+1:03}-{start+len(group):03}.jpg',quality=92)
    crops=[(p['pdfPage'],c) for p in result['pages'] for c in p['illustrations']]
    for start in range(0,len(crops),60):
        group=crops[start:start+60]
        sheet=Image.new('RGB',(1500,10*160),'#e8e8e8')
        draw=ImageDraw.Draw(sheet)
        for index,(page,crop) in enumerate(group):
            image=Image.open(media/crop['image'].split('/media/tv1-tap1/')[1]).convert('RGB')
            image.thumbnail((240,136))
            x,y=(index%6)*250,(index//6)*160
            sheet.paste(image,(x,y))
            draw.text((x+3,y+139),crop['image'].split('/')[-1],fill='black')
        sheet.save(args.qa_dir/f'figures-{start+1:03}-{start+len(group):03}.jpg',quality=92)
    print(json.dumps({'pages':173,'illustrations':len(crops),'assets':173+len(crops),'manifest':str(data)}))


if __name__=='__main__':
    main()
