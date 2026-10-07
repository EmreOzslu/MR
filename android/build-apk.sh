#!/bin/sh
# APK üretir: aapt2 + javac + dx + apksig. Android SDK / Gradle gerektirmez.
# Gerçek geliştirme için Android Studio'da android/ klasörünü açıp Gradle ile derlemek daha uygundur.
#
# Gerekli araçlar (TOOLS dizininde ya da ilgili ortam değişkeniyle):
#   AAPT2        aaptjs3 npm paketindeki linux x64 aapt2 ikilisi
#   DX_JAR       com.google.android.tools:dx:1.7 (Maven Central)
#   APKSIG_JAR   com.android.tools.build:apksig (Maven Central)
#   ANDROID_JAR  org.robolectric:android-all:14-robolectric-10818077 (framework sınıfları + resources.arsc)
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/.." && pwd)
TOOLS=${TOOLS:-$HOME/androidtools}
AAPT2=${AAPT2:-$TOOLS/aapt2}
DX_JAR=${DX_JAR:-$TOOLS/dx.jar}
APKSIG_JAR=${APKSIG_JAR:-$TOOLS/apksig.jar}
ANDROID_JAR=${ANDROID_JAR:-$TOOLS/android-all-14.jar}

OUT=$HERE/build
KEYSTORE=$HOME/.android/debug.keystore
APK=$OUT/Decadence-debug.apk

for f in "$AAPT2" "$DX_JAR" "$APKSIG_JAR" "$ANDROID_JAR"; do
    [ -e "$f" ] || { echo "Eksik araç: $f" >&2; exit 1; }
done

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/signer"

echo "[1/5] Kaynaklar derleniyor ve bağlanıyor"
"$AAPT2" compile --dir "$HERE/app/src/main/res" -o "$OUT/res.zip"
"$AAPT2" link -I "$ANDROID_JAR" \
    --manifest "$HERE/app/src/main/AndroidManifest.xml" \
    --java "$OUT/gen" \
    --min-sdk-version 26 --target-sdk-version 34 \
    --version-code 1 --version-name 0.1.0 \
    -o "$OUT/base.apk" "$OUT/res.zip"

echo "[2/5] Java derleniyor (core + app + R)"
find "$ROOT/core/src/main/java" "$HERE/app/src/main/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac --release 8 -Xlint:-options -cp "$ANDROID_JAR" -d "$OUT/classes" @"$OUT/sources.txt"

echo "[3/5] Dex üretiliyor ve APK'ya ekleniyor"
python3 "$HERE/tools/patch_class_version.py" "$OUT/classes"
java -cp "$DX_JAR" com.android.dx.command.Main --dex --output="$OUT/dex/classes.dex" "$OUT/classes"
python3 "$HERE/tools/add_dex.py" "$OUT/base.apk" "$OUT/dex/classes.dex" "$OUT/app-unsigned.apk"

echo "[4/5] İmzalanıyor (debug anahtarı)"
if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
        -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" \
        -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
javac -cp "$APKSIG_JAR" -d "$OUT/signer" "$HERE/tools/SignApk.java"
# apksig 2017 sürümü sun.security.x509'a erişiyor; JDK 9+ bunu varsayılan olarak dışa aktarmıyor.
APKSIG_RUN="java --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.pkcs=ALL-UNNAMED -cp $APKSIG_JAR:$OUT/signer SignApk"
$APKSIG_RUN sign "$KEYSTORE" androiddebugkey android "$OUT/app-unsigned.apk" "$APK"

echo "[5/5] Doğrulanıyor"
$APKSIG_RUN verify "$APK"
"$AAPT2" dump badging "$APK" | head -4
ls -l "$APK"
