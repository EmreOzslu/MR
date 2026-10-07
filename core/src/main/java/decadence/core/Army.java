package decadence.core;

/** Bir ordu. {@code owner}: 0 = NPC, 1..n = oyuncu. {@code dest}: -1 = hedef yok. */
public final class Army {
    final int owner;
    int at;
    int dest = -1;

    Army(int owner, int at) {
        this.owner = owner;
        this.at = at;
    }

    public int owner() { return owner; }
    public int at() { return at; }
    public int dest() { return dest; }
}
