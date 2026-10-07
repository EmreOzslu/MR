package decadence.core;

/** Yerleşim (şehir, kale ya da köy). Düğüm numarası konumunu verir; sahip olan fraksiyon {@code owner}. */
public final class Settlement {
    int owner;
    int pop;
    int food;
    int garrison;

    Settlement(int owner, int pop) {
        this.owner = owner;
        this.pop = pop;
        this.food = pop / 4;
        this.garrison = pop / 15;
    }

    public int owner() { return owner; }
    public int pop() { return pop; }
    public int food() { return food; }
    public int garrison() { return garrison; }
}
