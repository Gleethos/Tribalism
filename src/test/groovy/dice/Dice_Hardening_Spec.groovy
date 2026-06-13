package dice

import app.dice.DiceNotation
import app.dice.DiceRoll
import app.dice.DiceRoller
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title
import spock.lang.Unroll

import java.util.random.RandomGenerator

@Title("Dice — exact-value hardening / stable-API contract")
@Narrative('''

    These tests pin the precise, stable contract of the dice API using a scripted RNG so every
    face is known: exact totals, the "kept dice are returned in roll order" rule (uniform across
    keep modes), advantage/disadvantage, tie handling, and — importantly — that a critical hit/miss
    is detected only on a KEPT die (disadvantage that drops a natural 20 is not a crit).

''')
@CompileDynamic
class Dice_Hardening_Spec extends Specification
{
    /** Returns scripted nextInt(bound) values in order; bound is ignored (caller scripts faces-1). */
    static class ScriptedRandom implements RandomGenerator {
        private final Queue<Integer> q
        ScriptedRandom(List<Integer> nextIntReturns) { this.q = new LinkedList<>(nextIntReturns) }
        @Override int nextInt(int bound) {
            if (q.isEmpty()) throw new IllegalStateException("scripted RNG exhausted")
            return q.poll()
        }
        @Override long nextLong() { return 0L }
    }

    private static DiceRoll rollScripted(String notation, List<Integer> faces) {
        // faces are the desired die results (1..sides); DiceRoller adds 1 to nextInt, so script face-1.
        return new DiceRoller(new ScriptedRandom(faces.collect { it - 1 })).roll(notation)
    }

    def 'a simple pool plus a flat modifier totals exactly.'() {
        when : 'A 2d6 shows 4 and 2, plus 3.'
            var r = rollScripted("2d6+3", [4, 2])
        then :
            r.total() == 9
            r.terms().get(0).rolls().toList() == [4, 2]
            r.terms().get(0).kept().toList() == [4, 2]
            r.describe() == "2d6+3: (4, 2) + 3 = 9"
    }

    def 'a negative flat term subtracts.'() {
        expect :
            rollScripted("1d20-1", [15]).total() == 14
            rollScripted("1d20-1", [15]).describe() == "1d20-1: (15) - 1 = 14"
    }

    def 'multiple dice terms and a flat combine, including a second pool.'() {
        when : 'A 2d6 (6,2), a 1d4 (3), minus 1.'
            var r = rollScripted("2d6+1d4-1", [6, 2, 3])
        then :
            r.total() == 10
            r.terms().size() == 3
            r.terms().get(1).kept().toList() == [3]
    }

    def 'keep-highest drops the lowest and returns kept dice in ROLL order.'() {
        when : 'A 4d6 shows 2,5,3,6 keeping the highest 3.'
            var r = rollScripted("4d6kh3", [2, 5, 3, 6])
        then : 'The lowest (2) is dropped; the kept dice stay in their original order.'
            r.terms().get(0).rolls().toList() == [2, 5, 3, 6]
            r.terms().get(0).kept().toList() == [5, 3, 6]
            r.total() == 14
            r.describe() == "4d6kh3: (5, 3, 6) = 14"
    }

    def 'keep-lowest drops the highest and returns kept dice in ROLL order.'() {
        when : 'A 4d6 shows 2,5,3,6 keeping the lowest 2.'
            var r = rollScripted("4d6kl2", [2, 5, 3, 6])
        then :
            r.terms().get(0).kept().toList() == [2, 3]
            r.total() == 5
    }

    def 'ties in keep-highest break by earliest die, preserving roll order.'() {
        when : 'A 4d6 shows 3,3,3,6 keeping the highest 2.'
            var r = rollScripted("4d6kh2", [3, 3, 3, 6])
        then : 'The 6 and the first 3 are kept, in roll order.'
            r.terms().get(0).kept().toList() == [3, 6]
            r.total() == 9
    }

    @Unroll
    def 'advantage/disadvantage on a d20 keeps the right die (#notation of #faces -> #total).'() {
        expect :
            rollScripted(notation, faces).total() == total
        where :
            notation   | faces     || total
            "2d20kh1"  | [20, 5]   || 20     // advantage keeps the higher
            "2d20kl1"  | [20, 5]   || 5      // disadvantage keeps the lower
            "2d20kh1"  | [3, 18]   || 18
            "2d20kl1"  | [3, 18]   || 3
    }

    def 'a critical hit is only detected on a KEPT die.'() {
        expect : 'Advantage that keeps the 20 is a crit.'
            rollScripted("2d20kh1", [20, 5]).isCriticalHit()
        and : 'Disadvantage that DROPS the 20 (keeps 5) is NOT a crit.'
            !rollScripted("2d20kl1", [20, 5]).isCriticalHit()
        and : 'Disadvantage that keeps the 1 is a critical miss.'
            rollScripted("2d20kl1", [1, 11]).isCriticalMiss()
        and : 'A natural 20 on a flat 1d20 is a crit; a 1 is a miss.'
            rollScripted("1d20", [20]).isCriticalHit()
            rollScripted("1d20", [1]).isCriticalMiss()
        and : 'A 20 on a d12 is not a d20 crit (sides must match).'
            !rollScripted("1d12+8", [12]).isCriticalHit()
    }

    def 'a d1 always shows 1.'() {
        given :
            var r = DiceRoller.seeded(123L).roll("3d1")
        expect :
            r.terms().get(0).rolls().toList() == [1, 1, 1]
            r.total() == 3
    }

    @Unroll
    def 'canonical round-trips for "#text".'() {
        expect :
            DiceNotation.parse(text).canonical() == text
        where :
            text << ["10d10", "1d20kh1", "2d20kl1", "100d6", "1d6+2d8-3", "1d20+0"]
    }

    @Unroll
    def 'rejects the invalid notation "#bad".'() {
        when :
            DiceNotation.parse(bad)
        then :
            thrown(IllegalArgumentException)
        where :
            bad << ["5d6kh9", "0d6", "1d0", "1d6kh0", "2d6++3", "2d6+-3", "4d6kl0"]
    }

    def 'rolling never exceeds the notation bounds across many trials.'() {
        given :
            var roller = DiceRoller.seeded(2024L)
        expect : 'A 3d8+2 always lands within [5, 26] and a 4d6kh3 within [3, 18].'
            (1..200).every { roller.roll("3d8+2").total() in (5..26) }
            (1..200).every { roller.roll("4d6kh3").total() in (3..18) }
    }
}
