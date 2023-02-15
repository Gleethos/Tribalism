package app.models;

import dal.api.DataBase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AbilityTypes
{
    private final DataBase db;
    private final List<AbilityType> abilityTypes = new ArrayList<>();

    private AbilityTypes(DataBase db) {
        this.db = db;
        // We load the ability types in the order they are defined in the abilities.json file.
        var location = "/constants/abilities.json";
        String jsonText = null;
        // We load the json file into a string:
        try (var in = getClass().getResourceAsStream(location)) {
            assert in != null;
            jsonText = new String(in.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Could not load " + location);
        }
        // We load the ability types from the json file into a json object.
        var json = new org.json.JSONArray(jsonText);
        // We iterate over the ability types in the json object.
        for (int i = 0; i < json.length(); i++) {
            var newType = json.getJSONObject(i);
            var name        = newType.getString("name");
            var description = newType.getString("description");
            AbilityType abilityType = db.create(AbilityType.class);
            abilityType.name().set(name);
            abilityType.description().set(description);
            abilityTypes.add(abilityType);
        }
    }

    public static AbilityTypes load(DataBase db) {
        return new AbilityTypes(db);
    }

    public Map<String, AbilityType> getAbilityTypesByName() {
        Map<String, AbilityType> abilityTypesByName = new java.util.LinkedHashMap<>();
        for (AbilityType abilityType : abilityTypes) {
            abilityTypesByName.put(abilityType.name().get(), abilityType);
        }
        return abilityTypesByName;
    }
}
