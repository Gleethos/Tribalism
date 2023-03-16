package app.models.bootstrap;

import app.models.AbilityType;
import dal.api.DataBase;
import sprouts.Problem;
import sprouts.Result;

import java.util.*;

public class AbilityTypes extends AbstractTypes
{
    private static final String FILE_NAME = "ability-types.json";

    private final List<AbilityType> abilityTypes = new ArrayList<>();
    private final Map<String, AbilityType> abilityTypesByName = new HashMap<>();


    public AbilityTypes( DataBase db, String workingDirectory ) {
        super(workingDirectory, FILE_NAME);
        // We load the ability types in the order they are defined in the ability-types.json file.
        // The abilities are in the resource folder at src/main/resources/app/constants/ability-types.json
        // We check if the file already exists in the working directory!
        // If so, we load it from there, otherwise we load it from the resource folder.
        if ( localTypesExist() )
            loadFromWorkingDir(db);
        else
            loadFromResources(db);

        saveAsJSONToWorkingDirectory(workingDirectory + "/" + FILE_NAME, db);
    }

    @Override
    protected void loadFromLocation(String location, DataBase db ) {
        String jsonText = Util.readTextFile(location);
        // We load the ability types from the json file into a json object.
        var json = new org.json.JSONArray(jsonText);
        // We iterate over the ability types in the json object.
        for ( int i = 0; i < json.length(); i++ ) {
            var newType = json.getJSONObject(i);
            var name        = newType.getString("name");
            var description = newType.getString("description");
            // First we check if the ability type already exists in the database:
            var existingAbilityType = db.select(AbilityType.class)
                                            .where(AbilityType::name).is(name)
                                            .first();

            if ( existingAbilityType.isPresent() ) {
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

    public void saveAsJSONToWorkingDirectory( String location, DataBase db ) {
        var json = new org.json.JSONArray();
        for ( var abilityType : abilityTypes ) {
            var jsonAbilityType = new org.json.JSONObject();
            jsonAbilityType.put("name", abilityType.name().get());
            jsonAbilityType.put("description", abilityType.description().get());
            json.put(jsonAbilityType);
        }
        try ( var out = new java.io.FileWriter(location) ) {
            out.write(json.toString(4));
        } catch (Exception e) {
            throw new RuntimeException("Could not save " + FILE_NAME + " to working directory");
        }
    }

    @Override
    protected Result<Boolean> isDataBaseStateMatchingWorkingDirectory(DataBase db) {
        List<Problem> problems = new ArrayList<>();
        List<Problem> warnings = new ArrayList<>();
        List<AbilityType> foundInDB = db.selectAll(AbilityType.class);
        List<AbilityType> checked = new ArrayList<>();

        String jsonText = Util.readTextFile(workingDirectory + "/" + fileName);
        // We load the ability types from the json file into a json object.
        var json = new org.json.JSONArray(jsonText);
        // We iterate over the ability types in the json object.
        for ( int i = 0; i < json.length(); i++ ) {
            var newType     = json.getJSONObject(i);
            var name        = newType.getString("name");
            var description = newType.getString("description");
            // First we check if the ability type already exists in the database:
            boolean found = false;
            for ( var abilityType : foundInDB ) {
                if ( abilityType.name().is(name) ) {
                    if ( !abilityType.description().get().equals(description) ) {
                        warnings.add(Problem.of(
                            "Ability Type Inconsistency",
                            "Ability type '" + name + "' has a different description in the database.\n " +
                            "Found: '" + abilityType.description().get() + "'\n" +
                            "Expected: '" + description + "'"
                        ));
                    }
                    checked.add(abilityType);
                    found = true;
                    break;
                }
            }
            if ( !found )
                problems.add(Problem.of(
                    "Ability Type Missing",
                    "Ability type '" + name + "' is in the json file but not in the database."
                ));
        }

        if ( checked.size() != foundInDB.size() )
            for ( var abilityType : foundInDB )
                if ( !checked.contains(abilityType) )
                    problems.add(Problem.of(
                        "Ability Type Inconsistency",
                        "Ability type '" + abilityType.name().get() + "' is in the database but not in the json file."
                    ));


        if ( problems.size() > 0 ) {
            problems.addAll(warnings);
            return Result.of(false, problems);
        }

        return Result.of(true);
    }

    public Optional<AbilityType> findByName( String name ) {
        var abilityType = abilityTypesByName.get(name);
        if ( abilityType == null ) return Optional.empty();
        return Optional.of(abilityType);
    }

    public List<AbilityType> getAll() { return Collections.unmodifiableList(abilityTypes); }

    public boolean exists( String name ) { return abilityTypesByName.containsKey(name); }
}
