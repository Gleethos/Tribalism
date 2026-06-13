package app.models.sheet;

import dal.api.Value;
import sprouts.Tuple;

/**
 *  The rules-bearing state of a character, as a single immutable {@link Value} tree:
 *  identity, vitals, ability scores, skill scores, inventory and free-form notes.
 *  This is the first real value tree in the application and the proving ground for the
 *  data-oriented persistence model (see {@code VISION.md} §5.3 / §8.1):
 *  <ul>
 *      <li>It is persisted whole as one Topsoil {@code Value} hanging off a model root
 *          ({@code Character.sheet()}), deduplicated and reference-counted by content.</li>
 *      <li>The UI edits it entirely through {@code zoomTo} lenses on an in-memory
 *          {@code Var<CharacterSheet>} root, so a keystroke produces a brand-new sheet
 *          value (see {@code SWING_TREE_SKILL.md} §4).</li>
 *  </ul>
 *  Abilities and skills reference their types by name and are keyed by that name; the
 *  registries that describe those types ({@code AbilityType}/{@code SkillType}) live
 *  outside the sheet so the sheet stays self-contained and content-addressable.
 */
public record CharacterSheet(
    Identity              identity,
    Vitals                vitals,
    Tuple<AbilityScore>   abilities,
    Tuple<SkillScore>     skills,
    Tuple<InventoryItem>  inventory,
    String                notes
) implements Value {

    public CharacterSheet {
        if ( identity == null || vitals == null || abilities == null
          || skills == null || inventory == null || notes == null ) {
            throw new IllegalArgumentException("CharacterSheet components must not be null");
        }
    }

    /** @return A blank sheet: empty identity, starting vitals, no abilities/skills/inventory, no notes. */
    public static CharacterSheet empty() {
        return new CharacterSheet(
            Identity.empty(),
            Vitals.starting(),
            Tuple.of(AbilityScore.class),
            Tuple.of(SkillScore.class),
            Tuple.of(InventoryItem.class),
            ""
        );
    }

    public CharacterSheet withIdentity(Identity identity)               { return new CharacterSheet(identity, vitals, abilities, skills, inventory, notes); }
    public CharacterSheet withVitals(Vitals vitals)                     { return new CharacterSheet(identity, vitals, abilities, skills, inventory, notes); }
    public CharacterSheet withAbilities(Tuple<AbilityScore> abilities)  { return new CharacterSheet(identity, vitals, abilities, skills, inventory, notes); }
    public CharacterSheet withSkills(Tuple<SkillScore> skills)          { return new CharacterSheet(identity, vitals, abilities, skills, inventory, notes); }
    public CharacterSheet withInventory(Tuple<InventoryItem> inventory) { return new CharacterSheet(identity, vitals, abilities, skills, inventory, notes); }
    public CharacterSheet withNotes(String notes)                       { return new CharacterSheet(identity, vitals, abilities, skills, inventory, notes); }

    /**
     *  Sets (or inserts) the level of one ability by name, leaving the others untouched.
     *  @param ability The ability type name (e.g. {@code "strength"}).
     *  @param level The new level.
     *  @return A new sheet with that ability score updated.
     */
    public CharacterSheet withAbilityLevel(String ability, int level) {
        for ( int i = 0; i < abilities.size(); i++ ) {
            if ( abilities.get(i).ability().equals(ability) )
                return withAbilities(abilities.setAt(i, abilities.get(i).withLevel(level)));
        }
        return withAbilities(abilities.add(AbilityScore.of(ability, level)));
    }

    /**
     *  Sets (or inserts) the level of one skill by name, leaving the others untouched.
     *  @param skill The skill type name.
     *  @param level The new level.
     *  @return A new sheet with that skill score updated.
     */
    public CharacterSheet withSkillLevel(String skill, int level) {
        for ( int i = 0; i < skills.size(); i++ ) {
            if ( skills.get(i).skill().equals(skill) )
                return withSkills(skills.setAt(i, skills.get(i).withLevel(level)));
        }
        return withSkills(skills.add(SkillScore.of(skill, level)));
    }
}
