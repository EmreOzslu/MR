# Decadence – Android iskeleti

Kampanya çekirdeği (`core/`) üzerine kurulu, portre modda tek ekranlı Android uygulaması.

## Ne var
- **Oda:** "Yeni oda" ile kurulur (rastgele seed + şu anki an = gün 0). Oda, base64 tek satır kod olarak paylaşılır.
- **Katıl:** Arkadaşının kodu "Kodu gir" ile yapıştırılır. Boş cihazda Oyuncu 2 olarak katılır; odadaysa komutları birleştirir.
- **Harita:** 20×10 ızgara, düğümler arası yollar, NPC ordular gezinir, kendi ordun düğümlere doğru yürür. Gün içi hareket görsel olarak akar; dünya durumu tam sayı günlerle ilerler.
- **Kayıt:** Oda kodu ve oyuncu numarası `SharedPreferences` içinde tutulur; uygulama kapanınca oda kaybolmaz.

## Android Studio ile açmak
1. `android/` klasörünü açın. Gradle eklentisi (AGP 8.7.3) ilk açılışta indirilir.
2. Çekirdek kodu `app/build.gradle` içindeki `java.srcDirs` ile `../../core/src/main/java` olarak eklenir; ayrı bir kütüphane modülü yok.
3. Çalıştırmadan önce `compileSdk 34` platformunun SDK Manager'dan kurulu olması gerekir.

**Durum:** Gradle yolu bu sandbox'ta test edilmedi (Google Maven ve `dl.google.com` erişime kapalı). Dosyalar standart Android Studio şablonuna uygun yazıldı, ama ilk derlemede küçük düzeltme gerekebilir.

## Komut satırından APK (SDK'sız)
```
./build-apk.sh
```
Çıktı: `build/Decadence-debug.apk`. Script `aapt2`, `javac`, `dx` ve `apksig` kullanır; araç jar'larının konumu script başında yazılıdır (`TOOLS`, `AAPT2`, `DX_JAR`, `APKSIG_JAR`, `ANDROID_JAR`).

Bu yol sandbox'ta şu şekilde doğrulandı: APK üretildi, v2 imzası doğrulandı, manifest (min SDK 26, target 34) ve dex içinde uygulama ile çekirdek sınıfları var. **Henüz bir cihazda veya emülatörde çalıştırılmadı.**

Notlar:
- İmza debug anahtarıyla yapılır, yalnızca v2. Telefonda "bilinmeyen kaynaklardan yükle" izni gerekir.
- Framework için `android.jar` yerine Robolectric'in `android-all` jar'ı kullanıldı (içinde framework sınıfları ve `resources.arsc` var). Üretilen APK'nın çalışma zamanı cihazın kendi framework'ünü kullanır.

## Kod kuralları
- Java 8 sözdizimi; lambda ve method ref kullanılmıyor (eski `dx` bunları desteklemiyor). Core'daki `java.util.Base64` ve `Long.remainderUnsigned` nedeniyle `minSdk` 26.
- Oyun durumu UI'da değil `decadence.core` içinde. UI yalnızca `Campaign` üzerinden okur ve komut yollar.

## Bilinen eksikler
- Her saniye tam replay yapılıyor (gün değişince ya da komut gelince). Harita ve gün sayısı büyüdüğünde snapshot gerekir.
- Savaş, ekonomi, şehir yok. Ordular sadece hareket eder.
- Kod paylaşımı elle (paylaş menüsü). Otomatik senkron (Nearby Connections vb.) yok.
- Dokunma hedefi en yakın düğümü seçiyor; düğümler sık olduğunda yanlış düğüme gidebilir.
