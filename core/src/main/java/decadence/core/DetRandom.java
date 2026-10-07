package decadence.core;

/**
 * SplitMix64. Yalnızca tam sayı aritmetiği kullanır; her JVM'de ve her Android cihazda aynı diziyi üretir.
 */
public final class DetRandom {
    private long state;

    public DetRandom(long seed) {
        this.state = seed;
    }

    public long nextLong() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** [0, bound) aralığında tekdüze tam sayı. */
    public int nextInt(int bound) {
        return (int) Long.remainderUnsigned(nextLong(), bound);
    }

    /** Durumsuz karıştırma: gün bazlı tohumlar için (gün N'in RNG'si, önceki günlere bağlı değil). */
    public static long mix(long a, long b) {
        return new DetRandom(a ^ (b * 0x9E3779B97F4A7C15L)).nextLong();
    }
}
