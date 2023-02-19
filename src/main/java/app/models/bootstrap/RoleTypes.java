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

public class RoleTypes
{
    private static final Logger log = LoggerFactory.getLogger(RoleTypes.class);

    private final List<Role> roles = new ArrayList<>();
    private final Map<String, Role> rolesByName = new HashMap<>();
    private final String workingDirectory;

    public RoleTypes(
            DataBase db,
            String workingDirectory,
            AbilityTypes abilityTypes,
            SkillTypes skillTypes
    ) {
        this.workingDirectory = workingDirectory;
        // We load the roles in the order they are defined in the role-types.json file.
        // The roles are in the resource folder at src/main/resources/app/constants/role-types.json
        var location = "/app/bootstrap/role-types.json";
        // We check if the file already exists in the working directory!
        // If so, we load it from there, otherwise we load it from the resource folder.
        if ( new File(workingDirectory + "/role-types.json" ).exists() )
            location = workingDirectory + "/role-types.json";
        loadFromLocation(db, location, abilityTypes, skillTypes);
        saveAsJSONToWorkingDirectory(db);
    }

    private void loadFromLocation(
            DataBase db,
            String location,
            AbilityTypes abilityTypes,
            SkillTypes skillTypes
    ) {

        String jsonText = null;
        // We load the json file into a string:
        try (var in = getClass().getResourceAsStream(location)) {
            assert in != null;
            jsonText = new String(in.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Could not load " + location);
        }
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
        for (int i = 0; i < json.length(); i++) {
            var newRole = json.getJSONObject(i);
            var name        = newRole.getString("name");
            var description = newRole.getString("description");
            // First we check if the role already exists in the database:
            var existingRole = db.select(Role.class)
                                    .where(Role::name).is(name)
                                    .first();

            if (existingRole.isPresent()) {
                roles.add(existingRole.get());
                rolesByName.put(name, existingRole.get());
                continue;
            }
            Role role = db.create(Role.class);
            role.name().set(name);
            role.description().set(description);
            roles.add(role);
            rolesByName.put(name, role);
            // We load the abilities for the role:
            var abilities = newRole.getJSONArray("abilities");
            for (int j = 0; j < abilities.length(); j++) {
                var ability = abilities.getJSONObject(j);
                var abilityName = ability.getString("name");
                var abilityLevel = ability.getInt("level");
                var abilityType = abilityTypes.findByName(abilityName).orElseThrow();
                var newAbility = db.create(Ability.class);
                newAbility.type().set(abilityType);
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
                var skillType  = skillTypes.findByName(skillName).orElseThrow();
                var newSkill   = db.create(Skill.class);
                newSkill.type().set(skillType);
                newSkill.level().set(skillLevel);
                newSkill.isProficient().set(isProficient);
                newSkill.learnability().set(learnability);
            }
        }
    }

    private void saveAsJSONToWorkingDirectory(DataBase db) {
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
        try (var out = new java.io.FileWriter(workingDirectory + "/role-types.json")) {
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
