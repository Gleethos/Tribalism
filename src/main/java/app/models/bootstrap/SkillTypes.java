package app.models.bootstrap;

import app.models.SkillType;
import dal.api.DataBase;

import java.util.*;

public class SkillTypes extends AbstractTypes
{
    private static final String FILE_NAME = "skill-types.json";

    private final List<SkillType> skillTypes = new ArrayList<>();
    private final Map<String, SkillType> skillTypesByName = new HashMap<>();


    public SkillTypes(DataBase db, String workingDirectory) {
        super(workingDirectory, FILE_NAME);
        // We load the skill types in the order they are defined in the skills.json file.
        // The skills are in the resource folder at src/main/resources/app/constants/skill-types.json
        // We check if the file already exists in the working directory!
        // If so, we load it from there, otherwise we load it from the resource folder.
        if ( localTypesExist() )
            loadFromWorkingDir(db);
        else
            loadFromResources(db);

        saveAsJSONToWorkingDirectory(workingDirectory + "/" + FILE_NAME, db);
    }

    @Override
    protected void loadFromLocation(String location, DataBase db)
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

    @Override
    protected void saveAsJSONToWorkingDirectory(String location, DataBase db) {
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
        try (var out = new java.io.PrintWriter(location)) {
            out.println(json.toString(4));
        } catch (Exception e) {
            throw new RuntimeException("Could not save " + location, e);
        }
    }

    public Optional<SkillType> findByName(String name) {
        return Optional.ofNullable(skillTypesByName.get(name));
    }

    public List<SkillType> all() {
        return Collections.unmodifiableList(skillTypes);
    }

}
