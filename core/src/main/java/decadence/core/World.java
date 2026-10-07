package decadence.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Kampanya dünyası. Tüm kurallar tam sayı; float yok, Math.random yok, HashMap iterasyonu yok.
 * Gün sırası: NPC kararları -> hareket -> savaş ve kuşatma -> ikmal -> yerleşim ekonomisi.
 */
public final class World {
    public static final int COLS = 20;
    public static final int ROWS = 10;
    public static final int NODES = COLS * ROWS;
    /** Çizim alanı (dünya koordinatı). Düğüm konumu ızgara hücresi + küçük sapma. */
    public static final int CELL = 80;
    public static final int JITTER = 30;
    public static final int MAP_WIDTH = COLS * CELL;
    public static final int MAP_HEIGHT = ROWS * CELL;

    /** Fraksiyonlar: 1-2 oyuncular (müttefik), 3-6 krallıklar (birbirleriyle savaşta). */
    public static final int PLAYER_1 = 1;
    public static final int PLAYER_2 = 2;
    public static final int KINGDOM_FIRST = 3;
    public static final int KINGDOM_LAST = 6;
    private static final String[] NAMES = {
            "", "Oyuncu 1", "Oyuncu 2", "Kuzey Krallığı", "Doğu Krallığı", "Batı Krallığı", "Güney Krallığı"};

    static final int NPC_ARMIES_PER_KINGDOM = 10;
    static final int NPC_ARMIES = NPC_ARMIES_PER_KINGDOM * (KINGDOM_LAST - KINGDOM_FIRST + 1);
    public static final int PLAYER_ARMY_1 = NPC_ARMIES;
    public static final int PLAYER_ARMY_2 = NPC_ARMIES + 1;

    static final int AI_RANGE = 8;
    /** İlk günlerde krallık orduları toplanır ve hareket etmez; oyuncuya kurulma süresi tanınır. */
    static final int MUSTER_DAYS = 20;
    static final int PLAYER_MEN = 200;
    static final int MAX_POP = 1500;
    static final int TOWN_POP = 600;
    static final int DAYS_OF_FOOD = 20;
    /** Günlük iaşe: asker başına 1/20 birim. */
    static int dailyFood(int men) {
        return men / 20;
    }

    private final long seed;
    private final int[] x = new int[NODES];
    private final int[] y = new int[NODES];
    private final List<TreeSet<Integer>> adj = new ArrayList<>();
    private final List<Settlement> settlements = new ArrayList<>();
    private final List<Army> armies = new ArrayList<>();
    private final List<Command> rejected = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private long day = -1;

    private World(long seed) {
        this.seed = seed;
        for (int i = 0; i < NODES; i++) adj.add(new TreeSet<>());
    }

    public static World generate(long seed) {
        World w = new World(seed);
        DetRandom r = new DetRandom(seed);
        for (int i = 0; i < NODES; i++) {
            int col = i % COLS;
            int row = i / COLS;
            w.x[i] = col * CELL + r.nextInt(JITTER);
            w.y[i] = row * CELL + r.nextInt(JITTER);
            if (col + 1 < COLS) w.link(i, i + 1);
            if (row + 1 < ROWS) w.link(i, i + COLS);
            if (col + 1 < COLS && row + 1 < ROWS && r.nextInt(3) == 0) w.link(i, i + COLS + 1);
            w.settlements.add(new Settlement(quadrantOwner(col, row), 150 + r.nextInt(350)));
        }
        // Oyuncuların başlangıç kasabaları: köşe düğümler.
        w.settlements.set(0, startTown(PLAYER_1));
        w.settlements.set(NODES - 1, startTown(PLAYER_2));

        for (int k = 0; k < NPC_ARMIES; k++) {
            int owner = KINGDOM_FIRST + k % (KINGDOM_LAST - KINGDOM_FIRST + 1);
            List<Integer> own = w.nodesOwnedBy(owner);
            int at = own.get(r.nextInt(own.size()));
            int men = 80 + r.nextInt(120);
            w.armies.add(new Army(owner, at, men, dailyFood(men) * DAYS_OF_FOOD));
        }
        w.armies.add(new Army(PLAYER_1, 0, PLAYER_MEN, dailyFood(PLAYER_MEN) * DAYS_OF_FOOD));
        w.armies.add(new Army(PLAYER_2, NODES - 1, PLAYER_MEN, dailyFood(PLAYER_MEN) * DAYS_OF_FOOD));
        return w;
    }

    private static Settlement startTown(int owner) {
        Settlement s = new Settlement(owner, TOWN_POP);
        s.garrison = 80;
        s.food = 300;
        return s;
    }

    private static int quadrantOwner(int col, int row) {
        boolean north = row < ROWS / 2;
        boolean west = col < COLS / 2;
        if (north) return west ? KINGDOM_FIRST : KINGDOM_FIRST + 1;
        return west ? KINGDOM_FIRST + 2 : KINGDOM_LAST;
    }

    private List<Integer> nodesOwnedBy(int owner) {
        List<Integer> nodes = new ArrayList<>();
        for (int n = 0; n < NODES; n++) {
            if (settlements.get(n).owner == owner) nodes.add(n);
        }
        return nodes;
    }

    private void link(int a, int b) {
        adj.get(a).add(b);
        adj.get(b).add(a);
    }

    static boolean isPlayer(int owner) {
        return owner == PLAYER_1 || owner == PLAYER_2;
    }

    /**
     * Savaş blokları: oyuncular bir blok; Kuzey+Batı krallıkları bir blok; Doğu+Güney bir blok.
     * Aynı blok müttefiktir, farklı bloklar savaşta. Bu, dört krallığın herkesle aynı anda savaşmasını önler.
     */
    static int bloc(int owner) {
        if (isPlayer(owner)) return 1;
        return owner == KINGDOM_FIRST || owner == KINGDOM_FIRST + 2 ? 2 : 3;
    }

    static boolean hostile(int a, int b) {
        return bloc(a) != bloc(b);
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
        return a.alive()
                && a.owner == c.player()
                && c.target() >= 0 && c.target() < NODES
                && c.target() != a.at;
    }

    /** Bir gün ilerler. Bu günün oyuncu komutları önceden apply edilmiş olmalı. */
    void step(long today) {
        day = today;
        DetRandom r = new DetRandom(DetRandom.mix(seed, today));
        decideNpc(r);
        moveArmies();
        resolveCombat();
        supply();
        economy();
    }

    /** Krallık ordularının hedef seçimi. Skorlar tam sayı; eşitlikte küçük düğüm numarası kazanır. */
    private void decideNpc(DetRandom r) {
        if (day < MUSTER_DAYS) return;
        for (Army a : armies) {
            if (!a.alive() || a.owner < KINGDOM_FIRST || a.dest >= 0) continue;
            if (r.nextInt(4) == 0) continue; // bazı günler karar vermez; ordular senkron hareket etmesin
            Settlement here = settlements.get(a.at);
            boolean low = a.food < dailyFood(a.men) * 2;
            if (low && here.owner == a.owner && here.food > 0) continue; // ikmal için yerinde kal
            if (hostile(a.owner, here.owner) && here.garrison > 0) continue; // kuşatmayı sürdür

            int[] dist = distances(a.at, AI_RANGE);
            int best = Integer.MIN_VALUE;
            int target = -1;
            for (int n = 0; n < NODES; n++) {
                if (n == a.at || dist[n] < 0) continue;
                Settlement s = settlements.get(n);
                int score;
                if (low) {
                    if (s.owner != a.owner || s.food < dailyFood(a.men) * 2) continue;
                    score = 1000 + s.food / 10 - dist[n] * 10;
                } else if (hostile(a.owner, s.owner) && a.men > s.garrison * 2 + 10) {
                    score = s.pop / 10 - s.garrison * 2 - dist[n] * 3;
                } else {
                    continue;
                }
                if (score > best) {
                    best = score;
                    target = n;
                }
            }
            if (target >= 0) a.dest = target;
        }
    }

    private void moveArmies() {
        for (Army a : armies) {
            if (!a.alive() || a.dest < 0) continue;
            a.at = nextHop(a.at, a.dest);
            if (a.at == a.dest) a.dest = -1;
        }
    }

    /** Aynı düğümdeki düşman ordular savaşır; düşman yerleşimi garnizonuyla birlikte kuşatılır. */
    private void resolveCombat() {
        for (int n = 0; n < NODES; n++) {
            for (int i = 0; i < armies.size(); i++) {
                Army a = armies.get(i);
                if (!a.alive() || a.at != n) continue;
                for (int j = i + 1; j < armies.size(); j++) {
                    Army b = armies.get(j);
                    if (a.alive() && b.alive() && b.at == n && hostile(a.owner, b.owner)) fight(a, b, n);
                }
            }
            Settlement s = settlements.get(n);
            for (Army a : armies) {
                if (a.alive() && a.at == n && hostile(a.owner, s.owner)) siege(a, s, n);
            }
        }
    }

    private void fight(Army a, Army b, int n) {
        Army winner = a.men >= b.men ? a : b;
        Army loser = winner == a ? b : a;
        winner.men = Math.max(1, winner.men - loser.men / 2);
        event(name(loser.owner) + " ordusu " + name(winner.owner) + " ordusuna yenildi (düğüm " + n + ")");
        loser.men = 0;
    }

    private void siege(Army a, Settlement s, int n) {
        int attackerLoss = Math.min(a.men, s.garrison * 3 / 8);
        int defenderLoss = Math.min(s.garrison, a.men / 8);
        a.men -= attackerLoss;
        s.garrison -= defenderLoss;
        if (a.men <= 0) {
            a.men = 0;
            event(name(a.owner) + " ordusu düğüm " + n + " kuşatmasında yok oldu");
            return;
        }
        if (s.garrison == 0) {
            int previous = s.owner;
            s.owner = a.owner;
            s.garrison = Math.max(3, a.men / 8);
            event(name(a.owner) + ", " + name(previous) + " yerleşimini (düğüm " + n + ") ele geçirdi");
        }
    }

    /** Ordu ikmali: her gün iaşe tüketimi; yetmezse adam kaybı; yerleşimde durunca depodan tamamlanır. */
    private void supply() {
        for (Army a : armies) {
            if (!a.alive()) continue;
            a.food -= dailyFood(a.men);
            if (a.food < 0) {
                a.food = 0;
                a.men -= Math.max(1, a.men / 20);
                if (!a.alive()) {
                    a.men = 0;
                    event(name(a.owner) + " ordusu açlıktan dağıldı (düğüm " + a.at + ")");
                    continue;
                }
            }
            if (a.dest < 0) {
                Settlement s = settlements.get(a.at);
                if (s.owner == a.owner) {
                    int want = dailyFood(a.men) * DAYS_OF_FOOD - a.food;
                    int take = Math.min(s.food, Math.max(0, want));
                    s.food -= take;
                    a.food += take;
                }
            }
        }
    }

    /** Yerleşim: üretim, tüketim, açlık, nüfus artışı ve garnizon yenilenmesi. */
    private void economy() {
        for (Settlement s : settlements) {
            int food = s.food + s.pop / 9 - s.pop / 10;
            s.food = Math.max(0, Math.min(food, s.pop / 2 + 100));
            if (s.food == 0 && s.pop > 20) {
                s.pop -= Math.max(1, s.pop / 40);
            } else if (s.food > s.pop / 3 && s.pop < MAX_POP) {
                s.pop += s.pop / 400 + 1;
            }
            if (s.garrison < s.pop / 15) s.garrison++;
        }
    }

    private void event(String text) {
        events.add("Gün " + day + ": " + text);
    }

    static String name(int owner) {
        return NAMES[owner];
    }

    /** Verilen kaynaktan {@code maxDepth} adım içindeki düğümlerin uzaklığı; erişilmeyenler -1. */
    private int[] distances(int src, int maxDepth) {
        int[] dist = new int[NODES];
        Arrays.fill(dist, -1);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        dist[src] = 0;
        q.add(src);
        while (!q.isEmpty()) {
            int u = q.poll();
            if (dist[u] >= maxDepth) continue;
            for (int v : adj.get(u)) {
                if (dist[v] < 0) {
                    dist[v] = dist[u] + 1;
                    q.add(v);
                }
            }
        }
        return dist;
    }

    /** En kısa yoldaki bir sonraki düğüm. Eşitlikte en küçük numara seçilir. */
    public int nextHop(int from, int to) {
        int[] dist = distances(to, NODES);
        for (int v : adj.get(from)) {
            if (dist[v] == dist[from] - 1) return v;
        }
        return from;
    }

    /** Dünya durumunun parmak izi. İki cihazın aynı durumda olduğunu bu değerle karşılaştırırız. */
    public long hash() {
        long h = DetRandom.mix(seed, day);
        for (Settlement s : settlements) {
            h = DetRandom.mix(h, ((long) s.owner << 48) ^ ((long) s.pop << 24) ^ ((long) s.food << 12) ^ s.garrison);
        }
        for (Army a : armies) {
            h = DetRandom.mix(h, ((long) a.owner << 48) ^ ((long) a.at << 36) ^ ((long) (a.dest + 1) << 24)
                    ^ ((long) a.men << 12) ^ a.food);
        }
        return DetRandom.mix(h, events.size());
    }

    public int x(int node) { return x[node]; }
    public int y(int node) { return y[node]; }
    public Set<Integer> neighbors(int node) { return Collections.unmodifiableSet(adj.get(node)); }
    public Settlement settlement(int node) { return settlements.get(node); }

    public long seed() { return seed; }
    public long day() { return day; }
    public int armyCount() { return armies.size(); }
    public Army army(int i) { return armies.get(i); }
    public List<Command> rejected() { return Collections.unmodifiableList(rejected); }
    public List<String> events() { return Collections.unmodifiableList(events); }
}
