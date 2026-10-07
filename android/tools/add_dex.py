#!/usr/bin/env python3
"""aapt2 çıktısı base.apk'nın içine classes.dex ekler.

Sıkıştırılmamış (stored) girişlerin veri başlangıcı 4 bayta hizalanır (resources.arsc için Android 11+
şartı; zipalign -p ile aynı mantık). Hizalama, local header'daki extra alanıyla yapılır.
Kullanım: add_dex.py <base.apk> <classes.dex> <çıktı.apk>
"""
import struct
import sys
import zipfile

ALIGN_EXTRA_ID = 0xD935  # zipalign'ın kullandığı serbest extra alan kimliği


def padding_for(offset_after_name):
    """Extra alanı uzunluğu: veri başlangıcı 4'ün katı olacak şekilde (0 veya >= 4 bayt)."""
    k = (4 - offset_after_name % 4) % 4
    if k in (1, 2, 3):
        k += 4
    return k


def extra_bytes(k):
    if k == 0:
        return b''
    return struct.pack('<HH', ALIGN_EXTRA_ID, k - 4) + b'\0' * (k - 4)


src, dex, out = sys.argv[1:4]
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(out, 'w') as zout:
    for info in zin.infolist():
        data = zin.read(info.filename)
        if info.compress_type == zipfile.ZIP_STORED:
            header_end = zout.fp.tell() + 30 + len(info.filename.encode('utf-8'))
            info.extra = extra_bytes(padding_for(header_end))
        zout.writestr(info, data)
    zout.write(dex, 'classes.dex', compress_type=zipfile.ZIP_DEFLATED)
print('dex eklendi, arşiv: ' + out)
