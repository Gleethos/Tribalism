package app.dice;

import sprouts.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 *  Rolls a {@link DiceNotation} into a {@link DiceRoll}. The randomness source is injectable so
 *  rolls can be made deterministic for tests, replay, or auditable sessions ({@link #seeded(long)})
 *  or genuinely random ({@link #systemRandom()}).
 */
public final class DiceRoller
{
    private final RandomGenerator rng;

    public DiceRoller( RandomGenerator rng ) {
        if ( rng == null ) throw new IllegalArgumentException("random generator must not be null");
        this.rng = rng;
    }

    /** @return A roller with a fixed seed — same seed and notation produce the same roll. */
    public static DiceRoller seeded( long seed ) { return new DiceRoller(new Random(seed)); }

    /** @return A roller backed by a fresh system-seeded generator. */
    public static DiceRoller systemRandom() { return new DiceRoller(new Random()); }

    /** Rolls a textual expression (parsed via {@link DiceNotation#parse}). */
    public DiceRoll roll( String notation ) { return roll(DiceNotation.parse(notation)); }

    /** Rolls a parsed notation. */
    public DiceRoll roll( DiceNotation notation ) {
        List<TermRoll> termRolls = new ArrayList<>();
        int total = 0;
        for ( int i = 0; i < notation.terms().size(); i++ ) {
            TermRoll tr = rollTerm(notation.terms().get(i));
            termRolls.add(tr);
            total += tr.contribution();
        }
        return new DiceRoll(notation, Tuple.of(TermRoll.class, termRolls), total);
    }

    private TermRoll rollTerm( DiceTerm term ) {
        return switch ( term ) {
            case DiceTerm.Flat f -> new TermRoll(f, Tuple.of(Integer.class), Tuple.of(Integer.class), f.sign() * f.value());
            case DiceTerm.Roll r -> {
                List<Integer> rolls = new ArrayList<>(r.count());
                for ( int i = 0; i < r.count(); i++ )
                    rolls.add(1 + rng.nextInt(r.sides()));
                List<Integer> kept = keep(rolls, r.keep());
                int subtotal = 0;
                for ( int k : kept ) subtotal += k;
                yield new TermRoll(r, Tuple.of(Integer.class, rolls), Tuple.of(Integer.class, kept), r.sign() * subtotal);
            }
        };
    }

    private static List<Integer> keep( List<Integer> rolls, Keep keep ) {
        if ( keep.isAll() ) return rolls;
        List<Integer> sorted = new ArrayList<>(rolls);
        sorted.sort(keep.mode() == Keep.Mode.HIGHEST ? (a, b) -> b - a : (a, b) -> a - b);
        return new ArrayList<>(sorted.subList(0, Math.min(keep.n(), sorted.size())));
    }
}
