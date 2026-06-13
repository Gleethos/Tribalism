package app.dice;

import app.models.sheet.AbilityScore;
import app.models.sheet.CharacterSheet;
import app.models.sheet.SkillScore;
import sprouts.Tuple;

/**
 *  Sheet-linked dice checks: a {@code 1d20} roll plus a modifier read from a
 *  {@link CharacterSheet} (an ability or skill level). This is the bridge between the dice system
 *  (§7.4) and the character sheet (§6.3) — e.g. a Perception check pulls the Sensing/Perception
 *  skill level as its modifier.
 */
public final class Checks
{
    private Checks() {}

    /** Rolls {@code 1d20 + <ability level>} for the named ability (modifier 0 if absent). */
    public static DiceRoll abilityCheck( CharacterSheet sheet, String ability, DiceRoller roller ) {
        return roller.roll(DiceNotation.check(abilityModifier(sheet, ability)));
    }

    /** Rolls {@code 1d20 + <skill level>} for the named skill (modifier 0 if absent). */
    public static DiceRoll skillCheck( CharacterSheet sheet, String skill, DiceRoller roller ) {
        return roller.roll(DiceNotation.check(skillModifier(sheet, skill)));
    }

    /** @return The level of the named ability on the sheet, or 0 if the sheet has no such score. */
    public static int abilityModifier( CharacterSheet sheet, String ability ) {
        Tuple<AbilityScore> abilities = sheet.abilities();
        for ( int i = 0; i < abilities.size(); i++ )
            if ( abilities.get(i).ability().equals(ability) ) return abilities.get(i).level();
        return 0;
    }

    /** @return The level of the named skill on the sheet, or 0 if the sheet has no such score. */
    public static int skillModifier( CharacterSheet sheet, String skill ) {
        Tuple<SkillScore> skills = sheet.skills();
        for ( int i = 0; i < skills.size(); i++ )
            if ( skills.get(i).skill().equals(skill) ) return skills.get(i).level();
        return 0;
    }
}
