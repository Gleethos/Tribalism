package app.models.sheet;

import dal.api.Value;

/**
 *  The universal vital statistics of a character that are not modelled as abilities or
 *  skills: health, character level and accumulated experience. An immutable {@link Value}
 *  component of a {@link CharacterSheet}.
 */
public record Vitals(
    int currentHealth,
    int maxHealth,
    int level,
    int experience
) implements Value {

    public Vitals {
        if ( maxHealth < 0 || level < 0 || experience < 0 ) {
            throw new IllegalArgumentException("Vitals must not be negative (maxHealth/level/experience)");
        }
    }

    /** @return Default vitals: full health of 10, level 1, no experience. */
    public static Vitals starting() {
        return new Vitals(10, 10, 1, 0);
    }

    public Vitals withCurrentHealth(int currentHealth) { return new Vitals(currentHealth, maxHealth, level, experience); }
    public Vitals withMaxHealth(int maxHealth)         { return new Vitals(currentHealth, maxHealth, level, experience); }
    public Vitals withLevel(int level)                 { return new Vitals(currentHealth, maxHealth, level, experience); }
    public Vitals withExperience(int experience)       { return new Vitals(currentHealth, maxHealth, level, experience); }
}
