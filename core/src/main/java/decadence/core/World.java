package decadence.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Kampanya dünyası. Tüm değerler tam sayı; float yok, Math.random yok, HashMap iterasyonu yok.
 * Sıra her zaman indeks ya da artan node numarasına göre belirlenir.
 */
public final class World {
    public static final int NODES = 200;
    static final int EXTRA_EDGES = 150;
    static final int NPC_ARMIES = 40;
    static final int NPC_OWNER = 0;
    public static final int PLAYER_ARMY_1 = NPC_ARMIES;
    public static final int PLAYER_ARMY_2 = NPC_ARMIES + 1;

    private final long seed;
    private final List<TreeSet<Integer>> adj = new ArrayList<>();
    private final List<Army> armies = new ArrayList<>();
    private final List<Command> rejected = new ArrayList<>();
    private long day = -1;

    private World(long seed) {
        this.seed = seed;
        for (int i = 0; i < NODES; i++) adj.add(new TreeSet<>());
    }

    public static World generate(long seed) {
        World w = new World(seed);
        DetRandom r = new DetRandom(seed);
        for (int i = 0; i + 1 < NODES; i++) w.link(i, i + 1); // zincir: harita her zaman bağlı
        for (int i = 0; i < EXTRA_EDGES; i++) {
            int a = r.nextInt(NODES);
            int b = r.nextInt(NODES);
            if (a != b) w.link(a, b);
        }
        for (int i = 0; i < NPC_ARMIES; i++) w.armies.add(new Army(NPC_OWNER, r.nextInt(NODES)));
        w.armies.add(new Army(1, 0));
        w.armies.add(new Army(2, NODES - 1));
        return w;
    }

    private void link(int a, int b) {
        adj.get(a).add(b);
        adj.get(b).add(a);
    }

    /** Komutu uygular. Geçersizse reddedilir: durum değişmez, komut {@link #rejected()} listesine düşer. */
    void apply(Command c) {
        if (c.kind() == Command.MOVE && validMove(c)) {
            armies.get(c.army()).dest = c.target();
        } else {
            rejected.add(c);
        }
    }

    private boolean validMove(Command c) {
        if (c.army() < 0 || c.army() >= armies.size()) return false;
        Army a = armies.get(c.army());
        return a.owner == c.player()
                && c.target() >= 0 && c.target() < NODES
                && c.target() != a.at;
    }

    /** Bir gün ilerler: önce NPC kararları, sonra hareket. Bu günün komutları önceden apply edilmiş olmalı. */
    void step(long today) {
        day = today;
        DetRandom r = new DetRandom(DetRandom.mix(seed, today));
        for (Army a : armies) {
            if (a.owner == NPC_OWNER && a.dest < 0) {
                int t = r.nextInt(NODES);
                if (t != a.at && r.nextInt(3) == 0) a.dest = t;
            }
        }
        for (Army a : armies) {
            if (a.dest < 0) continue;
            a.at = nextHop(a.at, a.dest);
            if (a.at == a.dest) a.dest = -1;
        }
    }

    /** En kısa yoldaki bir sonraki düğüm. Eşitlikte en küçük numara seçilir. */
    int nextHop(int from, int to) {
        int[] dist = new int[NODES];
        Arrays.fill(dist, -1);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        dist[to] = 0;
        q.add(to);
        while (!q.isEmpty()) {
            int u = q.poll();
            for (int v : adj.get(u)) {
                if (dist[v] < 0) {
                    dist[v] = dist[u] + 1;
                    q.add(v);
                }
            }
        }
        for (int v : adj.get(from)) {
            if (dist[v] == dist[from] - 1) return v;
        }
        return from;
    }

    /** Dünya durumunun parmak izi. İki cihazın aynı durumda olduğunu bu değerle karşılaştırırız. */
    public long hash() {
        long h = DetRandom.mix(seed, day);
        for (Army a : armies) {
            h = DetRandom.mix(h, ((long) a.owner << 40) ^ ((long) a.at << 20) ^ (a.dest + 1L));
        }
        return h;
    }

    public long seed() { return seed; }
    public long day() { return day; }
    public int armyCount() { return armies.size(); }
    public Army army(int i) { return armies.get(i); }
    public List<Command> rejected() { return Collections.unmodifiableList(rejected); }
}
