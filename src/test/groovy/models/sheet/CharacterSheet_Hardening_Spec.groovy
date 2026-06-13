package models.sheet

import app.models.sheet.AbilityScore
import app.models.sheet.CharacterSheet
import app.models.sheet.Identity
import app.models.sheet.InventoryItem
import app.models.sheet.Vitals
import groovy.transform.CompileDynamic
import spock.lang.Specification
import spock.lang.Title
import spock.lang.Unroll

@Title("CharacterSheet — value invariants and validation (stable-API contract)")
@CompileDynamic
class CharacterSheet_Hardening_Spec extends Specification
{
    def 'value records reject null components.'() {
        when : new Identity(null, "x", "r", 1, 1.0, 1.0, "d", "i")
        then : thrown(IllegalArgumentException)

        when : CharacterSheet.empty().withNotes(null)
        then : thrown(IllegalArgumentException)
    }

    @Unroll
    def 'Vitals rejects negative #field.'() {
        when : make()
        then : thrown(IllegalArgumentException)
        where :
            field         | make
            "maxHealth"   | { -> new Vitals(5, -1, 1, 0) }
            "level"       | { -> new Vitals(5, 10, -1, 0) }
            "experience"  | { -> new Vitals(5, 10, 1, -5) }
    }

    def 'currentHealth may be negative (a downed character), maxHealth may not.'() {
        expect :
            new Vitals(-3, 10, 1, 0).currentHealth() == -3
    }

    def 'withAbilityLevel updates in place without reordering or duplicating.'() {
        given :
            var sheet = CharacterSheet.empty()
                .withAbilityLevel("strength", 10)
                .withAbilityLevel("dexterity", 12)
                .withAbilityLevel("wisdom", 8)
        when : 'We raise the middle ability.'
            sheet = sheet.withAbilityLevel("dexterity", 16)
        then : 'Order is preserved, no duplicate is created, and only dexterity changed.'
            sheet.abilities().toList().collect { it.ability() } == ["strength", "dexterity", "wisdom"]
            sheet.abilities().toList().collect { it.level() } == [10, 16, 8]
    }

    def 'withers do not mutate the original sheet and equal content compares equal.'() {
        given :
            var base = CharacterSheet.empty().withAbilityLevel("strength", 10)
        when :
            var changed = base.withAbilityLevel("strength", 11)
        then :
            base.abilities().get(0).level() == 10
            changed.abilities().get(0).level() == 11
            base == CharacterSheet.empty().withAbilityLevel("strength", 10)
            changed == base.withAbilityLevel("strength", 11)
    }

    def 'inventory items have distinct identity even with identical content.'() {
        expect :
            AbilityScore.of("strength", 3) == AbilityScore.of("strength", 3)   // by content
            InventoryItem.named("Torch") != InventoryItem.named("Torch")        // distinct uid
    }
}
