package decadence.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Oyunun tek gerçek kaynağı: seed + komut logu. Dünya durumu her seferinde logdan yeniden hesaplanır.
 * Sunucu yok. İki cihaz loglarını birleştirince aynı durumu hesaplar.
 */
public final class Campaign {
    private static final Comparator<Command> ORDER = Comparator
            .comparingLong(Command::day)
            .thenComparingInt(Command::player)
            .thenComparingInt(Command::seq);

    private final long seed;
    private final long epochMs;
    private final long dayMs;
    private final Map<Long, Command> log = new HashMap<>();

    /**
     * @param epochMs oda kurulduğu gerçek an (ms). Gün sayısı buradan hesaplanır.
     * @param dayMs   bir oyun gününün gerçek süresi (ms). Örn. 1 saat = 3_600_000.
     */
    public Campaign(long seed, long epochMs, long dayMs) {
        this.seed = seed;
        this.epochMs = epochMs;
        this.dayMs = dayMs;
    }

    /** Şu anki gerçek zamana karşılık gelen oyun günü. Negatif olmaz. */
    public long dayAt(long nowMs) {
        return Math.max(0, (nowMs - epochMs) / dayMs);
    }

    /**
     * Gün içindeki ilerleme (0..1). Yalnızca görsel interpolasyon için; dünya durumuna girmez,
     * dolayısıyla cihazlar arası farklılık yaratmaz.
     */
    public double dayFraction(long nowMs) {
        long d = nowMs - epochMs;
        if (d <= 0) return 0;
        return (d % dayMs) / (double) dayMs;
    }

    /** Yerel oyuncunun komutu. Komut, verildiği anın oyun gününe yazılır. */
    public Command issueMove(long nowMs, int player, int army, int target) {
        int seq = 1;
        for (Command c : log.values()) {
            if (c.player() == player && c.seq() >= seq) seq = c.seq() + 1;
        }
        Command c = new Command(dayAt(nowMs), player, seq, Command.MOVE, army, target);
        log.put(c.key(), c);
        return c;
    }

    /** Dış kaynaktan gelen komutları ekler. Zaten bilinenler atlanır. Eklenen sayıyı döndürür. */
    public int merge(Collection<Command> incoming) {
        int added = 0;
        for (Command c : incoming) {
            if (log.putIfAbsent(c.key(), c) == null) added++;
        }
        return added;
    }

    /** Verilen gün sonundaki dünya durumu. Baştan replay edilir. */
    public World stateAt(long day) {
        List<Command> cmds = sortedLog();
        World w = World.generate(seed);
        int i = 0;
        for (long d = 0; d <= day; d++) {
            while (i < cmds.size() && cmds.get(i).day() <= d) {
                w.apply(cmds.get(i++));
            }
            w.step(d);
        }
        return w;
    }

    /** Paylaşılabilir tek satırlık oda kodu: başlık + tüm komutlar, Base64. */
    public String exportCode() {
        StringBuilder sb = new StringBuilder();
        sb.append("H,").append(seed).append(',').append(epochMs).append(',').append(dayMs).append('\n');
        for (Command c : sortedLog()) sb.append(c.encode()).append('\n');
        return Base64.getEncoder().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Oda kodundan yeni bir kampanya kurar. */
    public static Campaign fromCode(String code) {
        String[] lines = decodeLines(code);
        String[] h = lines[0].split(",");
        if (h.length != 4 || !h[0].equals("H")) throw new IllegalArgumentException("Geçersiz oda kodu");
        Campaign c = new Campaign(Long.parseLong(h[1]), Long.parseLong(h[2]), Long.parseLong(h[3]));
        c.merge(parseCommands(lines));
        return c;
    }

    /** Arkadaşınızın kodunu bu kampanyaya ekler. Farklı bir odaya ait kodu reddeder. */
    public int importCode(String code) {
        Campaign other = fromCode(code);
        if (other.seed != seed || other.epochMs != epochMs || other.dayMs != dayMs) {
            throw new IllegalArgumentException("Bu kod başka bir odaya ait");
        }
        return merge(other.log.values());
    }

    public long seed() { return seed; }

    private List<Command> sortedLog() {
        List<Command> l = new ArrayList<>(log.values());
        l.sort(ORDER);
        return l;
    }

    private static String[] decodeLines(String code) {
        String text = new String(Base64.getDecoder().decode(code.trim()), StandardCharsets.UTF_8);
        return text.split("\n");
    }

    private static List<Command> parseCommands(String[] lines) {
        List<Command> cmds = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isEmpty()) cmds.add(Command.decode(lines[i]));
        }
        return cmds;
    }
}
