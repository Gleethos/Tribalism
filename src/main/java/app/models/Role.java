package app.models;

import dal.api.Value;

/** The immutable descriptive state of a {@link RoleModel} (its skill/ability modifiers are relations). */
public record Role(String name, String description) implements Value {
    public Role {
        if ( name == null || description == null )
            throw new IllegalArgumentException("Role fields must not be null");
    }
    public static Role empty() { return new Role("", ""); }
    public Role withName(String name)               { return new Role(name, description); }
    public Role withDescription(String description) { return new Role(name, description); }
}
