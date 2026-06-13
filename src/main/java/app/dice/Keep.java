package app.dice;

/**
 *  Which dice of a pool to keep after rolling — the basis for advantage/disadvantage and
 *  "keep highest/lowest N" mechanics. {@link Mode#ALL} keeps every die; {@link Mode#HIGHEST}
 *  / {@link Mode#LOWEST} keep the {@code n} best/worst.
 */
public record Keep(Mode mode, int n) {

    public enum Mode { ALL, HIGHEST, LOWEST }

    public Keep {
        if ( mode != Mode.ALL && n <= 0 )
            throw new IllegalArgumentException("keep count must be positive for HIGHEST/LOWEST");
    }

    /** Keep every die. */
    public static final Keep ALL = new Keep(Mode.ALL, 0);

    public static Keep highest( int n ) { return new Keep(Mode.HIGHEST, n); }
    public static Keep lowest( int n )  { return new Keep(Mode.LOWEST, n); }

    public boolean isAll() { return mode == Mode.ALL; }
}
