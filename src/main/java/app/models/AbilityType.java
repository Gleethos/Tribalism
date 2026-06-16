package app.models;

import dal.api.Value;

/** The immutable descriptive state of an {@link AbilityTypeModel}. */
public record AbilityType(String name, String description) implements Value {
    public AbilityType {
        if ( name == null || description == null )
            throw new IllegalArgumentException("AbilityType fields must not be null");
    }
    public static AbilityType empty() { return new AbilityType("", ""); }
    public AbilityType withName(String name) { return new AbilityType(name, description); }
    public AbilityType withDescription(String description) { return new AbilityType(name, description); }
}
