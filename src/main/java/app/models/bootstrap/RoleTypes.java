package app.models.bootstrap;

import app.models.*;
import dal.api.DataBase;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoleTypes
{
    private static Logger log = LoggerFactory.getLogger(RoleTypes.class);
    private static RoleTypes _INSTANCE = null;

    private final List<Role> roles = new ArrayList<>();
    private final Map<String, Role> rolesByName = new HashMap<>();

    public static RoleTypes load(DataBase db) {
        if (_INSTANCE != null) return _INSTANCE;
        _INSTANCE = new RoleTypes(db);
        return _INSTANCE;
    }


    private RoleTypes(DataBase db) {
        AbilityTypes abilityTypes = AbilityTypes.load(db);
        SkillTypes skillTypes = SkillTypes.load(db);
        // We load the roles in the order they are defined in the role-types.json file.
        // The roles are in the resource folder at src/main/resources/app/constants/role-types.json
        var location = "/app/bootstrap/role-types.json";
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
                "skills": [
                  {
                    "type": "jump", // references the skill type by name
                    "level": 0
                  }, {
                    "type": "swim",
                    "level": 1
                  },
                  ...
                ]
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
                var skillType  = skillTypes.findByName(skillName).orElseThrow();
                var newSkill   = db.create(Skill.class);
                newSkill.type().set(skillType);
                newSkill.level().set(skillLevel);
            }
        }
    }

    public List<Role> all() {
        return roles;
    }

    public Role findByName(String name) {
        return rolesByName.get(name);
    }

}
