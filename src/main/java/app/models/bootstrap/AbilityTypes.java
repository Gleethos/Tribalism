package app.models.bootstrap;

import app.models.AbilityType;
import dal.api.DataBase;

import java.util.*;

public class AbilityTypes
{
    private static AbilityTypes _INSTANCE = null;

    private final List<AbilityType> abilityTypes = new ArrayList<>();
    private final Map<String, AbilityType> abilityTypesByName = new HashMap<>();


    public static AbilityTypes load(DataBase db) {
        if (_INSTANCE != null) return _INSTANCE;
        _INSTANCE = new AbilityTypes(db);
        return _INSTANCE;
    }


    private AbilityTypes(DataBase db) {
        // We load the ability types in the order they are defined in the ability-types.json file.
        // The abilities are in the resource folder at src/main/resources/app/constants/ability-types.json
        var location = "/app/bootstrap/ability-types.json";
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
            // First we check if the ability type already exists in the database:
            var existingAbilityType = db.select(AbilityType.class)
                                            .where(AbilityType::name).is(name)
                                            .first();

            if (existingAbilityType.isPresent()) {
                abilityTypes.add(existingAbilityType.get());
                abilityTypesByName.put(name, existingAbilityType.get());
                continue;
            }
            AbilityType abilityType = db.create(AbilityType.class);
            abilityType.name().set(name);
            abilityType.description().set(description);
            abilityTypes.add(abilityType);
            abilityTypesByName.put(name, abilityType);
        }
    }

    public Optional<AbilityType> findByName(String name) {
        var abilityType = abilityTypesByName.get(name);
        if (abilityType == null) return Optional.empty();
        return Optional.of(abilityType);
    }

    public List<AbilityType> getAll() {
        return Collections.unmodifiableList(abilityTypes);
    }
}
