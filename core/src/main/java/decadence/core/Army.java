package decadence.core;

/**
 * Bir ordu. {@code owner}: fraksiyon (1-2 oyuncular, 3-6 krallıklar). {@code dest}: -1 = hedef yok.
 * {@code men}: asker sayısı. {@code food}: taşınan yiyecek (1 birim = 1 asker-günlük iaşe için 10 asker = 1 birim/gün).
 */
public final class Army {
    final int owner;
    int at;
    int dest = -1;
    int men;
    int food;

    Army(int owner, int at, int men, int food) {
        this.owner = owner;
        this.at = at;
        this.men = men;
        this.food = food;
    }

    public int owner() { return owner; }
    public int at() { return at; }
    public int dest() { return dest; }
    public int men() { return men; }
    public int food() { return food; }
    public boolean alive() { return men > 0; }
}
