package dice

import app.dice.Checks
import app.dice.DiceNotation
import app.dice.DiceRoll
import app.dice.DiceRoller
import app.dice.DiceTerm
import app.dice.Keep
import app.models.sheet.CharacterSheet
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title
import spock.lang.Unroll

import java.util.random.RandomGenerator

@Title("The dice system")
@Narrative('''

    Pure, data-oriented dice: a DiceNotation parser (NdS, keep-highest/lowest, +/- terms), a
    DiceRoller with injectable randomness (so rolls are deterministic for tests / replay /
    auditable sessions), crit helpers, and sheet-linked ability/skill checks.

''')
@CompileDynamic
class Dice_Spec extends Specification
{
    /** A RandomGenerator whose nextInt(bound) returns scripted values, for controlled dice faces. */
    static class ScriptedRandom implements RandomGenerator {
        private final Queue<Integer> faces
        ScriptedRandom(List<Integer> nextIntReturns) { this.faces = new LinkedList<>(nextIntReturns) }
        @Override int nextInt(int bound) { return faces.poll() }
        @Override long nextLong() { return 0L }
    }

    @Unroll
    def 'parses "#text" and round-trips to canonical "#canonical".'() {
        expect :
            DiceNotation.parse(text).canonical() == canonical
        where :
            text          || canonical
            "2d6+3"       || "2d6+3"
            "1d20"        || "1d20"
            "1d20+5"      || "1d20+5"
            "4d6kh3"      || "4d6kh3"
            "2d20kl1"     || "2d20kl1"
            "1d20+1d4-1"  || "1d20+1d4-1"
            " 2 d 6 + 3 " || "2d6+3"
            "2D6+3"       || "2d6+3"
            "-1d4+2"      || "-1d4+2"
    }

    @Unroll
    def 'rejects the malformed notation "#bad".'() {
        when :
            DiceNotation.parse(bad)
        then :
            thrown(IllegalArgumentException)
        where :
            bad << ["", "   ", "d6", "2d", "2d6+", "2d6*5", "abc", "2d6kh"]
    }

    def 'rolling is deterministic for a fixed seed.'() {
        given :
            var a = DiceRoller.seeded(42L)
            var b = DiceRoller.seeded(42L)
        expect :
            a.roll("3d6+2") == b.roll("3d6+2")
    }

    def 'every die lands in range and the total is within the notation bounds.'() {
        given :
            var roller = DiceRoller.seeded(7L)
        when : 'We roll 2d6+3 a hundred times.'
            var rolls = (1..100).collect { roller.roll("2d6+3") }
        then : 'Every individual die is between 1 and 6, and every total between 5 and 15.'
            rolls.every { DiceRoll r ->
                r.terms().get(0).rolls().toList().every { it >= 1 && it <= 6 } &&
                r.total() >= 5 && r.total() <= 15
            }
    }

    def 'keep-highest drops the lowest dice; keep-lowest drops the highest.'() {
        given :
            var roller = DiceRoller.seeded(123L)
        when : 'We roll 4d6 keeping the highest 3.'
            var kh = roller.roll("4d6kh3")
        then : 'Four dice were rolled, three were kept, and the kept sum is the total of the 3 highest.'
            kh.terms().get(0).rolls().size() == 4
            kh.terms().get(0).kept().size() == 3
            var rolled = kh.terms().get(0).rolls().toList().sort(false)
            kh.total() == rolled[1] + rolled[2] + rolled[3]   // drop the single lowest
        when : 'We roll 4d6 keeping the lowest 1.'
            var kl = roller.roll("4d6kl1")
        then : 'One die kept, equal to the minimum rolled.'
            kl.terms().get(0).kept().size() == 1
            kl.total() == kl.terms().get(0).rolls().toList().min()
    }

    def 'a negative term subtracts from the total.'() {
        given :
            var roller = DiceRoller.seeded(1L)
        expect : 'A 1d20-1 total is the d20 face minus one.'
            var r = roller.roll("1d20-1")
            r.total() == r.terms().get(0).kept().get(0) - 1
    }

    def 'critical hit and miss are detected on a kept d20.'() {
        when : 'A scripted d20 shows 20 (nextInt(20) -> 19).'
            var crit = new DiceRoller(new ScriptedRandom([19])).roll("1d20")
        then :
            crit.isCriticalHit()
            !crit.isCriticalMiss()
            crit.total() == 20
        when : 'A scripted d20 shows 1 (nextInt(20) -> 0).'
            var fumble = new DiceRoller(new ScriptedRandom([0])).roll("1d20")
        then :
            fumble.isCriticalMiss()
            !fumble.isCriticalHit()
    }

    def 'a sheet skill check rolls 1d20 plus the skill level as modifier.'() {
        given : 'A sheet with a known skill level, and a scripted d20 showing 10 (nextInt(20)->9).'
            var sheet = CharacterSheet.empty().withSkillLevel("Aim", 4).withAbilityLevel("dexterity", 3)
            var roller = new DiceRoller(new ScriptedRandom([9]))
        when :
            var check = Checks.skillCheck(sheet, "Aim", roller)
        then : 'The total is 10 + 4 = 14.'
            check.total() == 14
            check.notation().canonical() == "1d20+4"
        and : 'An ability check pulls the ability level.'
            Checks.abilityModifier(sheet, "dexterity") == 3
        and : 'An unknown skill contributes a zero modifier (just the d20).'
            Checks.skillModifier(sheet, "Nonexistent") == 0
            DiceNotation.check(0).canonical() == "1d20"
    }

    def 'describe() renders a readable breakdown.'() {
        given : 'A scripted 2d6 showing 4 and 2, then +3.'
            var roller = new DiceRoller(new ScriptedRandom([3, 1]))   // nextInt(6) -> 3,1 => faces 4,2
        when :
            var r = roller.roll("2d6+3")
        then :
            r.total() == 9
            r.describe() == "2d6+3: (4, 2) + 3 = 9"
    }

    def 'value records compare by content.'() {
        expect :
            DiceTerm.Roll.of(2, 6) == new DiceTerm.Roll(1, 2, 6, Keep.ALL)
            DiceNotation.parse("2d6+3") == DiceNotation.parse("2d6+3")
    }
}
