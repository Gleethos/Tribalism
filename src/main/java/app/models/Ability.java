package app.models;

import dal.api.Value;

/** The immutable level state of an {@link AbilityModel} (its type is a separate model relation). */
public record Ability(int level) implements Value {
    public static Ability empty() { return new Ability(0); }
    public Ability withLevel(int level) { return new Ability(level); }
}
