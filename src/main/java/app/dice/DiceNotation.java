package app.dice;

import sprouts.Tuple;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *  An immutable, parsed dice expression — an ordered list of signed {@link DiceTerm}s, e.g.
 *  {@code 2d6+3}, {@code 1d20+5}, {@code 4d6kh3} (keep highest 3), {@code 2d20kl1}
 *  (disadvantage), {@code 1d20+1d4-1}.
 *  <p>
 *  Grammar (whitespace ignored): a sequence of terms separated/prefixed by {@code +}/{@code -};
 *  each term is either {@code N d S [kh M | kl M]} (a dice pool) or {@code N} (a flat constant).
 */
public record DiceNotation(Tuple<DiceTerm> terms) {

    public DiceNotation {
        if ( terms == null || terms.isEmpty() )
            throw new IllegalArgumentException("a dice notation must have at least one term");
    }

    // One signed term: sign, count, then optionally 'd' sides and an optional keep clause.
    private static final Pattern TERM = Pattern.compile("([+-]?)(\\d+)(?:[dD](\\d+)(?:(kh|kl)(\\d+))?)?");

    /**
     *  Parses a textual dice expression.
     *  @param text e.g. {@code "2d6+3"}; whitespace and case (for {@code d}) are ignored.
     *  @return The parsed notation.
     *  @throws IllegalArgumentException if the text is empty or malformed.
     */
    public static DiceNotation parse( String text ) {
        if ( text == null ) throw new IllegalArgumentException("notation text must not be null");
        String s = text.replaceAll("\\s+", "");
        if ( s.isEmpty() ) throw new IllegalArgumentException("empty dice notation");

        Matcher m = TERM.matcher(s);
        java.util.List<DiceTerm> parsed = new java.util.ArrayList<>();
        int pos = 0;
        boolean first = true;
        while ( pos < s.length() ) {
            if ( !m.find(pos) || m.start() != pos )
                throw new IllegalArgumentException("malformed dice notation '" + text + "' at: '" + s.substring(pos) + "'");
            String signStr = m.group(1);
            if ( first && signStr.isEmpty() ) signStr = "+";
            if ( signStr.isEmpty() )
                throw new IllegalArgumentException("missing + or - between terms in '" + text + "'");
            int sign = signStr.equals("-") ? -1 : 1;

            int n = Integer.parseInt(m.group(2));
            if ( m.group(3) != null ) {
                int sides = Integer.parseInt(m.group(3));
                Keep keep = Keep.ALL;
                if ( m.group(4) != null ) {
                    int kn = Integer.parseInt(m.group(5));
                    keep = m.group(4).equals("kh") ? Keep.highest(kn) : Keep.lowest(kn);
                }
                parsed.add(new DiceTerm.Roll(sign, n, sides, keep));
            } else {
                parsed.add(new DiceTerm.Flat(sign, n));
            }
            pos = m.end();
            first = false;
        }
        return new DiceNotation(Tuple.of(DiceTerm.class, parsed));
    }

    /** @return A 1d20 check with a signed modifier (the basis of ability/skill checks). */
    public static DiceNotation check( int modifier ) {
        if ( modifier == 0 )
            return new DiceNotation(Tuple.of(DiceTerm.class, java.util.List.of(DiceTerm.Roll.of(1, 20))));
        return new DiceNotation(Tuple.of(DiceTerm.class,
                java.util.List.of(DiceTerm.Roll.of(1, 20), DiceTerm.Flat.of(modifier))));
    }

    /** @return The canonical textual form, e.g. {@code "2d6+3"}. */
    public String canonical() {
        StringBuilder sb = new StringBuilder();
        for ( int i = 0; i < terms.size(); i++ ) {
            DiceTerm t = terms.get(i);
            if ( i > 0 || t.sign() < 0 ) sb.append(t.sign() < 0 ? "-" : "+");
            switch ( t ) {
                case DiceTerm.Roll r -> {
                    sb.append(r.count()).append('d').append(r.sides());
                    switch ( r.keep().mode() ) {
                        case HIGHEST -> sb.append("kh").append(r.keep().n());
                        case LOWEST  -> sb.append("kl").append(r.keep().n());
                        case ALL     -> { }
                    }
                }
                case DiceTerm.Flat f -> sb.append(f.value());
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() { return canonical(); }
}
