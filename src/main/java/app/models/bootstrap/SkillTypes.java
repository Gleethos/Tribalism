package app.models.bootstrap;

import app.models.SkillType;
import dal.api.DataBase;

import java.util.*;

public class SkillTypes
{
    private final List<SkillType> skillTypes = new ArrayList<>();
    private final Map<String, SkillType> skillTypesByName = new HashMap<>();


    public SkillTypes(DataBase db, String workingDirectory) {
        // We load the skill types in the order they are defined in the skills.json file.
        // The skills are in the resource folder at src/main/resources/app/constants/skill-types.json
        var location = "/app/bootstrap/skill-types.json";
        String jsonText = null;
        // We load the json file into a string:
        try (var in = getClass().getResourceAsStream(location)) {
            assert in != null;
            jsonText = new String(in.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Could not load " + location);
        }
        // We load the skill types from the json file into a json object.
        var json = new org.json.JSONArray(jsonText);
        // We iterate over the skill types in the json object.
        for (int i = 0; i < json.length(); i++) {
            var newType = json.getJSONObject(i);
            var name        = newType.getString("name");
            var description = newType.getString("description");
            // First we check if the skill type already exists in the database:
            var existingSkillType = db.select(SkillType.class)
                                            .where(SkillType::name).is(name)
                                            .first();

            if (existingSkillType.isPresent()) {
                skillTypes.add(existingSkillType.get());
                skillTypesByName.put(name, existingSkillType.get());
                continue;
            }
            SkillType skillType = db.create(SkillType.class);
            skillType.name().set(name);
            skillType.description().set(description);
            skillTypes.add(skillType);
            skillTypesByName.put(name, skillType);
        }
    }

    public Optional<SkillType> findByName(String name) {
        var skillType = skillTypesByName.get(name);
        return skillType == null ? Optional.empty() : Optional.of(skillType);
    }

    public List<SkillType> all() {
        return Collections.unmodifiableList(skillTypes);
    }

}
