package app.models;

import dal.api.DataBase;

public class Roles {

    private final DataBase db;

    private Roles(DataBase db) { this.db = db; }

    public Roles abilities(
        AbilityTypes types,
        int strength,
        int dexterity,
        int constitution,
        int intelligence,
        int wisdom,
        int charisma,
        int sensing
    ) {

        return this;
    }

    public Roles skill(String name, String description, Integer value) {
        Skill skill = db.create(Skill.class);
        skill.name().set(name);
        skill.description().set(description);
        skill.value().set(value);
        return this;
    }

    public static void load(DataBase db, AbilityTypes types) {
        var strength     = types.getAbilityTypesByName().get("Strength");
        var dexterity    = types.getAbilityTypesByName().get("Dexterity");
        var constitution = types.getAbilityTypesByName().get("Constitution");
        var intelligence = types.getAbilityTypesByName().get("Intelligence");
        var wisdom       = types.getAbilityTypesByName().get("Wisdom");
        var charisma     = types.getAbilityTypesByName().get("Charisma");
        var sensing      = types.getAbilityTypesByName().get("Sensing");
        new Roles(db);
        //TODO: Load the roles.
    }

}
