#!/bin/sh
# Robolectric testlerini JVM içinde çalıştırır (emülatör gerekmez).
# Önce ./build-apk.sh çalışmış olmalı: build/classes uygulama + core sınıflarını içerir.
# Bağımlılıklar: TEST_LIBS dizininde Robolectric 4.14.1 ve JUnit 4.13.2 (Maven Central'dan).
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
TOOLS=${TOOLS:-$HOME/androidtools}
ANDROID_JAR=${ANDROID_JAR:-$TOOLS/android-all-14.jar}
TEST_LIBS=${TEST_LIBS:-$TOOLS/testdeps/lib}
OUT=$HERE/build

[ -d "$OUT/classes" ] || { echo "Önce ./build-apk.sh çalıştır" >&2; exit 1; }

rm -rf "$OUT/test-classes" && mkdir -p "$OUT/test-classes"
# Yer tutucu (sadece sandbox): gerçek androidx.test jar'ı bu ortamda yok. Bkz. tools/test-shim.
javac --release 8 -Xlint:-options -cp "$OUT/classes:$ANDROID_JAR:$TEST_LIBS/*" \
    -d "$OUT/test-classes" $(find "$HERE/tools/test-shim" "$HERE/app/src/test/java" -name '*.java')

# Çalışma dizini app/ olmalı: Robolectric manifest yolunu oradan çözer.
cd "$HERE/app"
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED \
    -cp "$OUT/test-classes:$OUT/classes:$TEST_LIBS/*:$ANDROID_JAR" \
    org.junit.runner.JUnitCore dev.decadence.campaign.MainActivityTest
