package decadence.core;

/**
 * Oyuncu komutu. {@code day}: komutun geçerli olduğu oyun günü (gerçek saate göre hesaplanır).
 * {@code seq}: oyuncunun kendi sayacı; {@code (player, seq)} komutun kimliğidir.
 */
public final class Command {
    public static final int MOVE = 1;

    private final long day;
    private final int player;
    private final int seq;
    private final int kind;
    private final int army;
    private final int target;

    public Command(long day, int player, int seq, int kind, int army, int target) {
        this.day = day;
        this.player = player;
        this.seq = seq;
        this.kind = kind;
        this.army = army;
        this.target = target;
    }

    public long day() { return day; }
    public int player() { return player; }
    public int seq() { return seq; }
    public int kind() { return kind; }
    public int army() { return army; }
    public int target() { return target; }

    /** Kimlik: aynı komut iki cihazdan gelse bile tek sayılır. */
    long key() {
        return ((long) player << 32) | (seq & 0xffffffffL);
    }

    String encode() {
        return day + "," + player + "," + seq + "," + kind + "," + army + "," + target;
    }

    static Command decode(String line) {
        String[] p = line.split(",");
        return new Command(
                Long.parseLong(p[0]),
                Integer.parseInt(p[1]),
                Integer.parseInt(p[2]),
                Integer.parseInt(p[3]),
                Integer.parseInt(p[4]),
                Integer.parseInt(p[5]));
    }

    @Override
    public String toString() {
        return "Command(" + encode() + ")";
    }
}
