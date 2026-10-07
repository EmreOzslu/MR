#!/usr/bin/env python3
"""Sınıf dosyalarının major sürümünü 52 (Java 8) -> 50 yapar.

Neden: eski dx 1.7 aracı Java 8 class dosyalarını reddediyor. Kodda invokedynamic, lambda,
method ref ya da default method yok; yani class yapısı Java 6/7 ile aynı ve bu dönüşüm güvenli.
Sadece dx ile dex üretirken kullanılır; Gradle/D8 yolunda gerekmez.
"""
import os
import sys

root = sys.argv[1]
patched = 0
for d, _, files in os.walk(root):
    for f in files:
        if not f.endswith('.class'):
            continue
        path = os.path.join(d, f)
        with open(path, 'rb') as fh:
            data = bytearray(fh.read())
        if data[:4] != b'\xca\xfe\xba\xbe':
            raise SystemExit('class magic yok: ' + path)
        if data[6:8] == b'\x00\x34':
            data[6:8] = b'\x00\x32'
            with open(path, 'wb') as fh:
                fh.write(bytes(data))
            patched += 1
print('sürüm düzeltildi: %d sınıf' % patched)
