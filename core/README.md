# Decadence – kampanya çekirdeği (prototip)

Asenkron, gerçek zamanlı, sunucusuz iki kişilik kampanya dünyası.

## Model
- **Kaynak:** `seed` + komut logu. Dünya durumu her seferinde logdan replay edilir.
- **Zaman:** `dayAt(now) = (now - epoch) / dayMs`. Dünya gün sayısına göre kendiliğinden ilerler; iki oyuncunun aynı anda online olması gerekmez.
- **Görsel akıcılık:** `dayFraction(now)` yalnızca çizim interpolasyonu için. State'e girmez.
- **Senkron:** `exportCode()` / `importCode()`: Base64 tek satır. Mesajlaşma uygulamasıyla gönderilir.
- **Çakışma:** Komutlar `(day, player, seq)` sırasıyla uygulanır. Aynı anda gelen çelişen emirlerde sıra sonradan gelen tarafından değil, bu sıralamayla belirlenir.

## Determinizm kuralları
- Yalnızca tam sayı; float yok.
- Rastgelelik yalnızca `DetRandom` (SplitMix64), gün bazlı tohum: `mix(seed, day)`.
- `HashMap` yalnızca depolama için; iterasyon her zaman sıralı.
- Duvar saati yalnızca `dayAt` içinde; simülasyon içinde yok.

## Bilinçli eksikler (sonraki adımlar)
- Savaş, ekonomi, şehir sahipliği yok. Yalnızca ordu hareketi ve NPC gezinmesi var.
- `stateAt` her çağrıda baştan replay ediyor. Harita büyüdükçe periyodik snapshot gerekecek.
- Android UI yok. Bu katman saf Java 8 (record yok), Android projesine olduğu gibi girer.
- Kod taşıma yalnızca manuel. Sonra Nearby Connections ile otomatik alışveriş eklenebilir.

## Test
```
./run-tests.sh
```
