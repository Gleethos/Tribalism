package app.models;

import dal.api.Value;

/** The immutable descriptive state of a {@link SkillTypeModel}. */
public record SkillType(
    String name, String description,
    String primaryAbility, String secondaryAbility, String tertiaryAbility
) implements Value {
    public SkillType {
        if ( name == null || description == null || primaryAbility == null
          || secondaryAbility == null || tertiaryAbility == null )
            throw new IllegalArgumentException("SkillType fields must not be null");
    }
    public static SkillType empty() { return new SkillType("", "", "", "", ""); }
    public SkillType withName(String name)                         { return new SkillType(name, description, primaryAbility, secondaryAbility, tertiaryAbility); }
    public SkillType withDescription(String description)           { return new SkillType(name, description, primaryAbility, secondaryAbility, tertiaryAbility); }
    public SkillType withPrimaryAbility(String primaryAbility)     { return new SkillType(name, description, primaryAbility, secondaryAbility, tertiaryAbility); }
    public SkillType withSecondaryAbility(String secondaryAbility) { return new SkillType(name, description, primaryAbility, secondaryAbility, tertiaryAbility); }
    public SkillType withTertiaryAbility(String tertiaryAbility)   { return new SkillType(name, description, primaryAbility, secondaryAbility, tertiaryAbility); }
}
