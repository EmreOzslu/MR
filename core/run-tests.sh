#!/bin/sh
# Çekirdek testlerini derleyip çalıştırır. Bağımlılık yok, yalnızca JDK 8+.
set -e
cd "$(dirname "$0")"
rm -rf build && mkdir -p build
javac --release 8 -d build $(find src -name '*.java')
java -cp build decadence.core.CoreTest
