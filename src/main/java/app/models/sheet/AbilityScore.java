package app.models.sheet;

import dal.api.Value;

/**
 *  A character's score in one ability, the immutable {@link Value} analogue of the
 *  {@code Ability} model. The ability is referenced by {@code name} (an {@code AbilityType}
 *  name such as {@code "strength"}), which is also its natural key: a character has at most
 *  one score per ability type, so a sheet's abilities can be addressed by name without a
 *  surrogate id.
 */
public record AbilityScore(
    String ability,
    int    level
) implements Value {

    public AbilityScore {
        if ( ability == null ) {
            throw new IllegalArgumentException("Ability name must not be null");
        }
    }

    public static AbilityScore of(String ability, int level) {
        return new AbilityScore(ability, level);
    }

    public AbilityScore withLevel(int level) { return new AbilityScore(ability, level); }
}
