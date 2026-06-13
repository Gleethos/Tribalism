package app.dice;

import sprouts.Tuple;

/**
 *  The immutable result of rolling a {@link DiceNotation}: the notation rolled, the per-term
 *  outcomes, and the total. Carries tabletop helpers for natural-20/natural-1 detection on d20
 *  rolls (critical hit/miss).
 *  <p>
 *  This is the mechanical result. When a roll happens in a live session it is wrapped by a
 *  message carrying the roller (a participant) and a visibility (public / party / whisper /
 *  GM-only) — see {@code VISION.md} §7.4.
 */
public record DiceRoll(
    DiceNotation     notation,
    Tuple<TermRoll>  terms,
    int              total
) {
    public DiceRoll {
        if ( notation == null || terms == null )
            throw new IllegalArgumentException("DiceRoll components must not be null");
    }

    /** @return Whether any kept die with {@code sides} faces shows {@code face}. */
    public boolean hasKeptDie( int sides, int face ) {
        for ( int i = 0; i < terms.size(); i++ ) {
            TermRoll tr = terms.get(i);
            if ( tr.term() instanceof DiceTerm.Roll r && r.sides() == sides )
                for ( Integer k : tr.kept() )
                    if ( k == face ) return true;
        }
        return false;
    }

    /** @return True if a kept d20 shows 20 (natural 20). */
    public boolean isCriticalHit()  { return hasKeptDie(20, 20); }

    /** @return True if a kept d20 shows 1 (natural 1). */
    public boolean isCriticalMiss() { return hasKeptDie(20, 1); }

    /** @return A readable breakdown, e.g. {@code "2d6+3: (4, 2) + 3 = 9"}. */
    public String describe() {
        StringBuilder sb = new StringBuilder(notation.canonical()).append(": ");
        for ( int i = 0; i < terms.size(); i++ ) {
            TermRoll tr = terms.get(i);
            if ( i > 0 ) sb.append(tr.term().sign() < 0 ? " - " : " + ");
            else if ( tr.term().sign() < 0 ) sb.append("-");
            if ( tr.term() instanceof DiceTerm.Roll ) sb.append('(').append(join(tr.kept())).append(')');
            else if ( tr.term() instanceof DiceTerm.Flat f ) sb.append(f.value());
        }
        return sb.append(" = ").append(total).toString();
    }

    private static String join( Tuple<Integer> xs ) {
        StringBuilder sb = new StringBuilder();
        for ( int i = 0; i < xs.size(); i++ ) {
            if ( i > 0 ) sb.append(", ");
            sb.append(xs.get(i));
        }
        return sb.toString();
    }
}
