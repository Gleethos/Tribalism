package app.models.bootstrap;

import app.models.SkillType;
import dal.api.DataBase;

import java.io.File;
import java.util.*;

public class SkillTypes
{
    private final List<SkillType> skillTypes = new ArrayList<>();
    private final Map<String, SkillType> skillTypesByName = new HashMap<>();
    private final String workingDirectory;


    public SkillTypes(DataBase db, String workingDirectory) {
        this.workingDirectory = workingDirectory;
        // We load the skill types in the order they are defined in the skills.json file.
        // The skills are in the resource folder at src/main/resources/app/constants/skill-types.json
        var location = "/app/bootstrap/skill-types.json";
        // We check if the file already exists in the working directory!
        // If so, we load it from there, otherwise we load it from the resource folder.
        if ( new File(workingDirectory + "/skill-types.json" ).exists())
            location = workingDirectory + "/skill-types.json";

        loadFromLocation(db, location);
        saveAsJSONToWorkingDirectory(db);
    }

    private void loadFromLocation(DataBase db, String location)
    {
        String jsonText = Util.readTextFile(location);
        // We load the skill types from the json file into a json object.
        var json = new org.json.JSONArray(jsonText);
        // We iterate over the skill types in the json object.
        for (int i = 0; i < json.length(); i++) {
            var newType = json.getJSONObject(i);
            var name        = newType.getString("name");
            var description = newType.getString("description");
            var primAbility = newType.getString("primary ability");
            var secAbility  = newType.getString("secondary ability");
            var terAbility  = newType.getString("tertiary ability");
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
            skillType.primaryAbility().set(primAbility);
            skillType.secondaryAbility().set(secAbility);
            skillType.tertiaryAbility().set(terAbility);
            skillTypes.add(skillType);
            skillTypesByName.put(name, skillType);
        }
    }

    public void saveAsJSONToWorkingDirectory(DataBase db) {
        var skillTypes = db.selectAll(SkillType.class);
        var json = new org.json.JSONArray();
        for (var skillType : skillTypes) {
            var jsonSkillType = new org.json.JSONObject();
            jsonSkillType.put("name", skillType.name().get());
            jsonSkillType.put("description", skillType.description().get());
            jsonSkillType.put("primary ability", skillType.primaryAbility().get());
            jsonSkillType.put("secondary ability", skillType.secondaryAbility().get());
            jsonSkillType.put("tertiary ability", skillType.tertiaryAbility().get());
            json.put(jsonSkillType);
        }
        var path = workingDirectory + "/skill-types.json";
        try (var out = new java.io.PrintWriter(path)) {
            out.println(json.toString(4));
        } catch (Exception e) {
            throw new RuntimeException("Could not save " + path);
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
