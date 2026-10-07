#!/usr/bin/env python3
"""aapt2 çıktısı base.apk'nın içine classes.dex ekler.

Mevcut girişler (manifest, resources.arsc, res/...) ZipInfo ile aynen kopyalanır; sıkıştırma türleri korunur.
Kullanım: add_dex.py <base.apk> <classes.dex> <çıktı.apk>
"""
import sys
import zipfile

src, dex, out = sys.argv[1:4]
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(out, 'w') as zout:
    for info in zin.infolist():
        zout.writestr(info, zin.read(info.filename))
    zout.write(dex, 'classes.dex', compress_type=zipfile.ZIP_DEFLATED)
print('dex eklendi: ' + out)
