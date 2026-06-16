package app.user;

import app.models.sheet.AbilityScore;
import app.models.sheet.CharacterSheet;
import app.models.sheet.Identity;
import app.models.sheet.InventoryItem;
import app.models.sheet.SkillScore;
import app.models.sheet.Vitals;
import sprouts.Tuple;
import sprouts.Var;

import java.util.Objects;

/**
 *  An MVI/MVL view model for editing a {@link CharacterSheet}: it holds the editing root as a
 *  single {@code Var<CharacterSheet>} and exposes one {@code zoomTo} lens per editable field, so
 *  the view is a thin binder and the lenses are unit-testable on their own (see
 *  {@code SWING_TREE_SKILL.md} §4 and {@code VISION.md} §5.3).
 *  <p>
 *  Exposing the lenses as {@code Var}-returning methods (rather than burying them in the view)
 *  also makes the sheet bindable over the websocket MVVM bridge — the same view model can drive
 *  the desktop and (once the bridge gains collection/lens support, {@code VISION.md} §9.2) the
 *  web character sheet.
 *  <p>
 *  The root may be an in-memory {@code Var<CharacterSheet>} (demo/test) or a persisted
 *  {@code CharacterModel.sheet()} property; in the latter case every edit writes a new sheet value
 *  straight to the database. The view model imports no Swing types.
 */
public final class CharacterSheetViewModel
{
    private final Var<CharacterSheet> sheet;

    // Identity field lenses:
    private final Var<String>  forename;
    private final Var<String>  surname;
    private final Var<String>  role;
    private final Var<Integer> age;
    private final Var<Double>  height;
    private final Var<Double>  weight;
    private final Var<String>  description;

    // Vitals field lenses:
    private final Var<Integer> currentHealth;
    private final Var<Integer> maxHealth;
    private final Var<Integer> level;
    private final Var<Integer> experience;

    private final Var<String> notes;
    private final Var<Tuple<InventoryItem>> inventory;

    public CharacterSheetViewModel( Var<CharacterSheet> sheet ) {
        this.sheet = Objects.requireNonNull(sheet);

        Var<Identity> identity = sheet.zoomTo(CharacterSheet::identity, CharacterSheet::withIdentity);
        this.forename    = identity.zoomTo(Identity::forename, Identity::withForename);
        this.surname     = identity.zoomTo(Identity::surname, Identity::withSurname);
        this.role        = identity.zoomTo(Identity::role, Identity::withRole);
        this.age         = identity.zoomTo(Identity::age, Identity::withAge);
        this.height      = identity.zoomTo(Identity::height, Identity::withHeight);
        this.weight      = identity.zoomTo(Identity::weight, Identity::withWeight);
        this.description = identity.zoomTo(Identity::description, Identity::withDescription);

        Var<Vitals> vitals = sheet.zoomTo(CharacterSheet::vitals, CharacterSheet::withVitals);
        this.currentHealth = vitals.zoomTo(Vitals::currentHealth, Vitals::withCurrentHealth);
        this.maxHealth     = vitals.zoomTo(Vitals::maxHealth, Vitals::withMaxHealth);
        this.level         = vitals.zoomTo(Vitals::level, Vitals::withLevel);
        this.experience    = vitals.zoomTo(Vitals::experience, Vitals::withExperience);

        this.notes     = sheet.zoomTo(CharacterSheet::notes, CharacterSheet::withNotes);
        this.inventory = sheet.zoomTo(CharacterSheet::inventory, CharacterSheet::withInventory);
    }

    /** @return A view model over a fresh, in-memory empty sheet. */
    public static CharacterSheetViewModel empty() {
        return new CharacterSheetViewModel(Var.of(CharacterSheet.empty()));
    }

    /** @return The editing root. */
    public Var<CharacterSheet> sheet() { return sheet; }

    public Var<String>  forename()    { return forename; }
    public Var<String>  surname()     { return surname; }
    public Var<String>  role()        { return role; }
    public Var<Integer> age()         { return age; }
    public Var<Double>  height()      { return height; }
    public Var<Double>  weight()      { return weight; }
    public Var<String>  description() { return description; }

    public Var<Integer> currentHealth() { return currentHealth; }
    public Var<Integer> maxHealth()     { return maxHealth; }
    public Var<Integer> level()         { return level; }
    public Var<Integer> experience()    { return experience; }

    public Var<String> notes()                  { return notes; }
    public Var<Tuple<InventoryItem>> inventory() { return inventory; }

    /** @return The ability scores currently on the sheet (a snapshot the view iterates over). */
    public Tuple<AbilityScore> abilities() { return sheet.get().abilities(); }

    /** @return The skill scores currently on the sheet (a snapshot the view iterates over). */
    public Tuple<SkillScore> skills() { return sheet.get().skills(); }

    /**
     *  A by-name logic lens onto one ability's level: reading finds the score by name,
     *  writing upserts it (insert if absent, replace level if present).
     *  @param ability The ability type name.
     *  @return A {@code Var<Integer>} editing just that ability's level on the root sheet.
     */
    public Var<Integer> abilityLevel( String ability ) {
        return sheet.zoomTo(
            s -> levelOf(s.abilities(), AbilityScore::ability, AbilityScore::level, ability),
            (s, lvl) -> s.withAbilityLevel(ability, lvl)
        );
    }

    /**
     *  A by-name logic lens onto one skill's level.
     *  @param skill The skill type name.
     *  @return A {@code Var<Integer>} editing just that skill's level on the root sheet.
     */
    public Var<Integer> skillLevel( String skill ) {
        return sheet.zoomTo(
            s -> levelOf(s.skills(), SkillScore::skill, SkillScore::level, skill),
            (s, lvl) -> s.withSkillLevel(skill, lvl)
        );
    }

    /** Appends a new, blank inventory item (with a fresh id) to the sheet. */
    public void addInventoryItem() {
        inventory.update(t -> t.add(InventoryItem.named("New item")));
    }

    /** Removes the given inventory item from the sheet. */
    public void removeInventoryItem( InventoryItem item ) {
        inventory.update(t -> t.remove(item));
    }

    private static <T> int levelOf(
        Tuple<T> scores,
        java.util.function.Function<T,String> nameOf,
        java.util.function.ToIntFunction<T> levelOf,
        String name
    ) {
        for ( int i = 0; i < scores.size(); i++ )
            if ( nameOf.apply(scores.get(i)).equals(name) ) return levelOf.applyAsInt(scores.get(i));
        return 0;
    }
}
