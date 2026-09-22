package one.nexus.permission.engine;

/** Field masking rank. Highest rank wins on merge (P0-02). */
public enum Mask {
    NONE(0),
    PARTIAL(1),
    FULL(2);

    private final int rank;

    Mask(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public static Mask strictest(Mask left, Mask right) {
        if (left == null) {
            return right == null ? NONE : right;
        }
        if (right == null) {
            return left;
        }
        return left.rank >= right.rank ? left : right;
    }
}
