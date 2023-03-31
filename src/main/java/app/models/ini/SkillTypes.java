package app.models.ini;

import app.models.SkillType;
import dal.api.DataBase;
import org.json.JSONArray;
import org.json.JSONObject;
import sprouts.Problem;
import sprouts.Result;

import java.util.*;

/**
 *  To let the application know about all the skill types that exist in the game,
 *  this class loads and verifies the skill types from the skill-types.json file
 *  as well as inside the database.
 *
 *  The first time the application is run, the skill types are loaded from the
 *  skill-types.json file in the resource folder. The skill types are then copied
 *  to the working directory and also persisted inside the game database.
 *  The reason for that is simple, the existence of the json file in the working
 *  directory indicates that the application has been run and set up at least once,
 *  so when it encounters the file, it can then verify the consistency of the
 *  skill types inside the database.
 */
public class SkillTypes extends AbstractTypes
{
    private static final String FILE_NAME = "skill-types.json";

    private final AbilityTypes abilityTypes;

    private final List<SkillType> skillTypes = new ArrayList<>();
    private final Map<String, SkillType> skillTypesByName = new HashMap<>();


    public SkillTypes(DataBase db, String workingDirectory, AbilityTypes abilityTypes) {
        super(workingDirectory, FILE_NAME);
        this.abilityTypes = abilityTypes;
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

    private void checkJson(JSONObject newType) {
        if ( !newType.has("name") )
            throw new RuntimeException("Failed to load skill type because it does not have a name.");
        if ( !newType.has("description") )
            throw new RuntimeException("Failed to load skill type because it does not have a description.");

        var name        = newType.getString("name");
        var primAbility = newType.getString("primary ability");
        var secAbility  = newType.getString("secondary ability");
        var terAbility  = newType.getString("tertiary ability");

        if ( !abilityTypes.exists(primAbility) )
            throw new RuntimeException("Found an unknown primary ability '" + primAbility + "' for skill type '" + name + "'.");
        if ( !abilityTypes.exists(secAbility) )
            throw new RuntimeException("Found an unknown secondary ability '" + secAbility + "' for skill type '" + name + "'.");
        if ( !abilityTypes.exists(terAbility) )
            throw new RuntimeException("Found an unknown tertiary ability '" + terAbility + "' for skill type '" + name + "'.");
    }

    @Override
    protected void loadFromLocation(String location, DataBase db)
    {
        String jsonText = Util.readTextFile(location);
        // We load the skill types from the json file into a json object.
        var json = new org.json.JSONArray(jsonText);
        // Let's check if the ability types we find are valid:
        for (int i = 0; i < json.length(); i++)
            checkJson(json.getJSONObject(i));

        // We iterate over the skill types in the json object again and add them to the database.
        for ( int i = 0; i < json.length(); i++ ) {
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

            SkillType skillType;

            if ( existingSkillType.isPresent() ) {
                skillType = existingSkillType.get();
                skillTypes.add(existingSkillType.get());
                skillTypesByName.put(name, existingSkillType.get());
            }
            else
                skillType = db.create(SkillType.class);

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
        for ( var skillType : skillTypes ) {
            var jsonSkillType = new org.json.JSONObject();
            jsonSkillType.put("name",              skillType.name().get());
            jsonSkillType.put("description",       skillType.description().get());
            jsonSkillType.put("primary ability",   skillType.primaryAbility().get());
            jsonSkillType.put("secondary ability", skillType.secondaryAbility().get());
            jsonSkillType.put("tertiary ability",  skillType.tertiaryAbility().get());
            json.put(jsonSkillType);
        }
        try ( var out = new java.io.PrintWriter(location) ) {
            out.println(json.toString(4));
        } catch (Exception e) {
            throw new RuntimeException("Could not save " + location, e);
        }
    }

    @Override
    protected Result<Boolean> isDataBaseStateMatchingWorkingDirectory(DataBase db) {
        List<Problem> problems = new ArrayList<>();
        List<Problem> warnings = new ArrayList<>();
        List<SkillType> foundInDB = db.selectAll(SkillType.class);
        List<SkillType> checked = new ArrayList<>();
        String jsonText = Util.readTextFile(workingDirectory + "/" + fileName);
        // We load the roles from the json file into a json object.
        JSONArray json = null;
        try {
            json = new JSONArray(jsonText);
        } catch (Exception e) {
            problems.add(Problem.of(e));
            return Result.of(false, problems);
        }

        // Let's check if the ability types we find are valid:
        for ( int i = 0; i < json.length(); i++ )
            try {
                checkJson(json.getJSONObject(i));
            } catch (Exception e) {
                problems.add(Problem.of("Skill Type Inconsistency", e.getMessage()));
            }

        // We iterate over the roles in the json object.
        for ( int i = 0; i < json.length(); i++ ) {
            var newType = json.getJSONObject(i);
            var name        = newType.getString("name");
            var description = newType.getString("description");
            var primAbility = newType.getString("primary ability");
            var secAbility  = newType.getString("secondary ability");
            var terAbility  = newType.getString("tertiary ability");

            // First we check if the role already exists in the database:
            boolean found = false;
            for ( var skillType : foundInDB ) {
                if ( skillType.name().get().equals(name) ) {
                    found = true;
                    checked.add(skillType);
                    if (!skillType.description().get().equals(description))
                        problems.add(Problem.of(
                                    "Skill Type Inconsistency",
                                    "Skill type " + name + " has a different description in " +
                                            "the database than in the file " + fileName
                                ));

                    if (!skillType.primaryAbility().get().equals(primAbility))
                        problems.add(Problem.of(
                                    "Skill Type Inconsistency",
                                    "Skill type " + name + " has a different primary ability in " +
                                            "the database than in the file " + fileName
                                ));

                    if (!skillType.secondaryAbility().get().equals(secAbility))
                        problems.add(Problem.of(
                                    "Skill Type Inconsistency",
                                    "Skill type " + name + " has a different secondary ability in " +
                                            "the database than in the file " + fileName
                                ));

                    if (!skillType.tertiaryAbility().get().equals(terAbility))
                        problems.add(Problem.of(
                                    "Skill Type Inconsistency",
                                    "Skill type " + name + " has a different tertiary ability in " +
                                            "the database than in the file " + fileName
                                ));

                    break;
                }
            }

            if ( !found )
                problems.add(Problem.of(
                        "Skill Type Missing",
                        "Skill type " + name + " is in the file " + fileName + " but not in the database"
                    ));
        }

        if ( checked.size() != foundInDB.size() )
            for ( var skillType : foundInDB )
                if ( !checked.contains(skillType) )
                    problems.add(Problem.of(
                            "Skill Type Missing",
                            "Skill type " + skillType.name().get() + " is in the database but not in the file " + fileName
                        ));

        if ( problems.size() > 0 ) {
            problems.addAll(warnings);
            return Result.of(false, problems);
        }

        return Result.of(true);
    }

    public Optional<SkillType> findByName(String name) { return Optional.ofNullable(skillTypesByName.get(name)); }

    public List<SkillType> all() { return Collections.unmodifiableList(skillTypes); }

}
