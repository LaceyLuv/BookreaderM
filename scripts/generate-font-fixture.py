#!/usr/bin/env python3
"""Own tiny CFF/OTF test font; fontTools is a generation tool, not an app dependency."""
from pathlib import Path
import hashlib, json
import fontTools
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.t2CharStringPen import T2CharStringPen
root=Path(__file__).resolve().parents[1]
path=root/'androidApp/src/debug/assets/font-fixtures/Fixture.otf'
path.parent.mkdir(parents=True,exist_ok=True)
builder=FontBuilder(1000,isTTF=False)
glyphs=['.notdef','space','A','uniD55C']
builder.setupGlyphOrder(glyphs)
builder.setupCharacterMap({32:'space',65:'A',0xD55C:'uniD55C'})
charstrings={}
for name in glyphs:
    pen=T2CharStringPen(600,None)
    if name!='space':
        pen.moveTo((80,0));pen.lineTo((520,0));pen.lineTo((520,700));pen.lineTo((80,700));pen.closePath()
    charstrings[name]=pen.getCharString()
builder.setupCFF('BookreaderFixture',{'FullName':'Bookreader Fixture','FamilyName':'Bookreader Fixture','Weight':'Regular'},charstrings,{})
builder.setupHorizontalMetrics({name:(600,0) for name in glyphs})
builder.setupHorizontalHeader(ascent=800,descent=-200)
builder.setupNameTable({'familyName':'Bookreader Fixture','styleName':'Regular','uniqueFontIdentifier':'BookreaderFixture-1','fullName':'Bookreader Fixture Regular','psName':'BookreaderFixture-Regular'})
builder.setupOS2(sTypoAscender=800,sTypoDescender=-200,usWinAscent=800,usWinDescent=200)
builder.setupPost()
builder.setupMaxp()
builder.font['head'].created=builder.font['head'].modified=3875644800
builder.font.recalcTimestamp=False
builder.save(path)
(root/'fixtures/font-manifest.json').write_text(json.dumps({'file':str(path.relative_to(root)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'bytes':path.stat().st_size,'source':'Own generated rectangular outlines from scripts/generate-font-fixture.py; no third-party outlines','fontTools_generation_version':fontTools.__version__},indent=2)+'\n')
