package app.models.bootstrap;

import app.models.*;
import dal.api.DataBase;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoleTypes extends AbstractTypes
{
    private static final Logger log = LoggerFactory.getLogger(RoleTypes.class);

    private final List<Role> roles = new ArrayList<>();
    private final Map<String, Role> rolesByName = new HashMap<>();

    private final AbilityTypes abilityTypes;
    private final SkillTypes skillTypes;

    public RoleTypes(
            DataBase db,
            String workingDirectory,
            AbilityTypes abilityTypes,
            SkillTypes skillTypes
    ) {
        super(workingDirectory, "role-types.json");
        this.abilityTypes = abilityTypes;
        this.skillTypes = skillTypes;
        // We load the roles in the order they are defined in the role-types.json file.
        // The roles are in the resource folder at src/main/resources/app/constants/role-types.json
        var location = "/app/bootstrap/role-types.json";
        // We check if the file already exists in the working directory!
        // If so, we load it from there, otherwise we load it from the resource folder.
        if ( new File(workingDirectory + "/role-types.json" ).exists() )
            location = workingDirectory + "/role-types.json";
        loadFromLocation(db, location, abilityTypes, skillTypes);
        saveAsJSONToWorkingDirectory(workingDirectory + "/role-types.json", db);
    }

    private void loadFromLocation(
            DataBase db,
            String location,
            AbilityTypes abilityTypes,
            SkillTypes skillTypes
    ) {

        String jsonText = Util.readTextFile(location);
        /*
           The json content might look something like this:
           [
              {
                "name": "Plumber",
                "description": "Plumbing is the installation and maintenance of pipes, fixtures, and other plumbing equipment used...",
                    "abilities":[
                      {"name": "strength",     "level": 1}, // References the ability type (by its name)
                      {"name": "dexterity",    "level": 3},
                      {"name": "constitution", "level": 0},
                      {"name": "intelligence", "level": 1},
                      {"name": "wisdom",       "level": 0},
                      {"name": "charisma",     "level": -1},
                      {"name": "sensing",      "level": -1},
                      {"name": "willpower",    "level": 1}
                    ],
                "skills": [...]
              },
              ...
            ]

         */
        // We load the roles from the json file into a json object.
        JSONArray json;
        try {
            json = new org.json.JSONArray(jsonText);
        } catch (Exception e) {
            log.error("Failed to parse 'role-types.json'!", e);
            throw e;
        }
        // We iterate over the roles in the json object.
        for ( int i = 0; i < json.length(); i++ ) {
            var newRole = json.getJSONObject(i);
            var name        = newRole.getString("name");
            var description = newRole.getString("description");
            // First we check if the role already exists in the database:
            var existingRole = db.select(Role.class)
                                    .where(Role::name).is(name)
                                    .first();

            Role role;

            if ( existingRole.isPresent() ) {
                rolesByName.put(name, existingRole.get());
                role = existingRole.get();
            }
            else {
                role = db.create(Role.class);
                role.name().set(name);
            }
            role.description().set(description);
            roles.add(role);
            rolesByName.put(name, role);

            // We load the abilities for the role:
            var abilities = newRole.getJSONArray("abilities");
            for (int j = 0; j < abilities.length(); j++) {
                var ability = abilities.getJSONObject(j);
                var abilityName = ability.getString("name");
                var abilityLevel = ability.getInt("level");
                Ability newAbility;
                // We check if the ability already exists in the role:
                var existingAbility = role.abilities()
                                            .stream()
                                            .filter(a -> a.type().get().name().get().equals(abilityName))
                                            .findFirst();

                if ( existingAbility.isPresent() )
                    newAbility = existingAbility.get();
                else {
                    var abilityType = abilityTypes.findByName(abilityName).orElseThrow();
                    newAbility = db.create(Ability.class);
                    newAbility.type().set(abilityType);
                    role.abilities().add(newAbility);
                }
                newAbility.level().set(abilityLevel);
            }

            // We load the skills for the role:
            var skills = newRole.getJSONArray("skills");
            for (int j = 0; j < skills.length(); j++) {
                var skill = skills.getJSONObject(j);
                var skillName  = skill.getString("name");
                var skillLevel = skill.getInt("level");
                var isProficient = skill.getBoolean("proficient");
                var learnability = skill.getDouble("learnability");
                Skill newSkill;

                // We check if the skill already exists in the role:
                var existingSkill = role.skills()
                                            .stream()
                                            .filter(s -> s.type().get().name().get().equals(skillName))
                                            .findFirst();

                if ( existingSkill.isPresent() )
                    newSkill = existingSkill.get();
                else {
                    var skillType  = skillTypes.findByName(skillName).orElseThrow();
                    newSkill = db.create(Skill.class);
                    newSkill.type().set(skillType);
                    role.skills().add(newSkill);
                }

                newSkill.level().set(skillLevel);
                newSkill.isProficient().set(isProficient);
                newSkill.learnability().set(learnability);
            }
        }
    }

    @Override
    protected void loadFromLocation(String location, DataBase db) {
        loadFromLocation(db, location, abilityTypes, skillTypes);
    }

    @Override
    protected void saveAsJSONToWorkingDirectory(String location, DataBase db) {
        var json = new JSONArray();
        for (var role : roles) {
            var jsonRole = new org.json.JSONObject();
            jsonRole.put("name", role.name().get());
            jsonRole.put("description", role.description().get());
            var jsonAbilities = new JSONArray();
            for (var ability : role.abilities()) {
                var jsonAbility = new org.json.JSONObject();
                jsonAbility.put("name", ability.type().get().name().get());
                jsonAbility.put("level", ability.level().get());
                jsonAbilities.put(jsonAbility);
            }
            jsonRole.put("abilities", jsonAbilities);
            var jsonSkills = new JSONArray();
            for (var skill : role.skills()) {
                var jsonSkill = new org.json.JSONObject();
                jsonSkill.put("name", skill.type().get().name().get());
                jsonSkill.put("level", skill.level().get());
                jsonSkill.put("proficient", skill.isProficient().get());
                jsonSkill.put("learnability", skill.learnability().get());
                jsonSkills.put(jsonSkill);
            }
            jsonRole.put("skills", jsonSkills);
            json.put(jsonRole);
        }
        try (var out = new java.io.FileWriter(location)) {
            out.write(json.toString(4));
        } catch (Exception e) {
            log.error("Failed to save 'role-types.json'!", e);
        }
    }

    public List<Role> all() {
        return roles;
    }

    public Role findByName(String name) {
        return rolesByName.get(name);
    }

}
