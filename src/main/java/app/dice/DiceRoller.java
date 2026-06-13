package app.dice;

import sprouts.Tuple;

import java.util.ArrayList;
import java.util.Comparator;
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

    /**
     *  Selects which dice count toward the subtotal, returning them in <b>roll order</b> (uniform
     *  with {@link Keep#ALL}). For HIGHEST/LOWEST it picks the n best/worst <em>by value</em>
     *  (ties broken by earliest roll) but preserves the original order of those kept, so a
     *  breakdown reads naturally and the ordering is consistent across all keep modes.
     */
    private static List<Integer> keep( List<Integer> rolls, Keep keep ) {
        if ( keep.isAll() ) return rolls;
        int n = Math.min(keep.n(), rolls.size());

        // Order indices by value (desc for HIGHEST, asc for LOWEST), tie-break by earlier index.
        Integer[] indices = new Integer[rolls.size()];
        for ( int i = 0; i < indices.length; i++ ) indices[i] = i;
        Comparator<Integer> byValue = keep.mode() == Keep.Mode.HIGHEST
                ? Comparator.<Integer>comparingInt(rolls::get).reversed().thenComparingInt(i -> i)
                : Comparator.<Integer>comparingInt(rolls::get).thenComparingInt(i -> i);
        java.util.Arrays.sort(indices, byValue);

        // Take the n winning indices, then restore roll order.
        List<Integer> keepIndices = new ArrayList<>(java.util.Arrays.asList(indices).subList(0, n));
        keepIndices.sort(Comparator.naturalOrder());

        List<Integer> kept = new ArrayList<>(n);
        for ( int i : keepIndices ) kept.add(rolls.get(i));
        return kept;
    }
}
