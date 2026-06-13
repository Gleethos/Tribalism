package app.dice;

import sprouts.Tuple;

/**
 *  The outcome of rolling one {@link DiceTerm}: the dice that were rolled (in roll order), the
 *  subset that were kept (per the term's {@link Keep}), and the term's signed contribution to the
 *  total. For a {@link DiceTerm.Flat} term, {@code rolls} and {@code kept} are empty and the
 *  contribution is the signed constant.
 */
public record TermRoll(
    DiceTerm        term,
    Tuple<Integer>  rolls,
    Tuple<Integer>  kept,
    int             contribution
) {
    public TermRoll {
        if ( term == null || rolls == null || kept == null )
            throw new IllegalArgumentException("TermRoll components must not be null");
    }
}
