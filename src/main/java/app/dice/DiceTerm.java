package app.dice;

/**
 *  One signed term of a {@link DiceNotation}: either a pool of dice ({@link Roll}, e.g.
 *  {@code 2d6} or {@code 2d20kh1} for advantage) or a flat constant ({@link Flat}, e.g.
 *  {@code +3}). A sum type implemented only by records.
 */
public sealed interface DiceTerm permits DiceTerm.Roll, DiceTerm.Flat {

    /** @return The sign of this term's contribution: {@code +1} or {@code -1}. */
    int sign();

    /** A pool of {@code count} dice with {@code sides} faces, of which {@code keep} are summed. */
    record Roll(int sign, int count, int sides, Keep keep) implements DiceTerm {
        public Roll {
            if ( sign != 1 && sign != -1 ) throw new IllegalArgumentException("sign must be +1 or -1");
            if ( count <= 0 ) throw new IllegalArgumentException("dice count must be positive");
            if ( sides <= 0 ) throw new IllegalArgumentException("dice sides must be positive");
            if ( keep == null ) throw new IllegalArgumentException("keep must not be null");
            if ( !keep.isAll() && keep.n() > count )
                throw new IllegalArgumentException("cannot keep more dice (" + keep.n() + ") than rolled (" + count + ")");
        }
        public static Roll of( int count, int sides ) { return new Roll(1, count, sides, Keep.ALL); }
    }

    /** A flat numeric modifier (no dice). */
    record Flat(int sign, int value) implements DiceTerm {
        public Flat {
            if ( sign != 1 && sign != -1 ) throw new IllegalArgumentException("sign must be +1 or -1");
            if ( value < 0 ) throw new IllegalArgumentException("flat value must be non-negative (use sign for direction)");
        }
        public static Flat of( int signedValue ) {
            return new Flat(signedValue < 0 ? -1 : 1, Math.abs(signedValue));
        }
    }
}
