package app.models.bootstrap;

import app.models.*;
import app.models.Character;

public class ModelTypes
{
    private final AbilityTypes abilityTypes;
    private final RoleTypes roleTypes;
    private final SkillTypes skillTypes;

    public ModelTypes(dal.api.DataBase db, String workingDirectory) {
        db.createTablesFor(
                Character.class,
                User.class,
                GameMaster.class,
                World.class,
                Player.class,
                CharacterModel.class,
                Ability.class,
                AbilityType.class,
                Skill.class,
                SkillType.class,
                Role.class
        );
        abilityTypes = new AbilityTypes(db, workingDirectory);
        skillTypes   = new SkillTypes(db, workingDirectory);
        roleTypes    = new RoleTypes(db, workingDirectory, abilityTypes, skillTypes);
    }

    public AbilityTypes abilityTypes() {
        return abilityTypes;
    }

    public RoleTypes roleTypes() {
        return roleTypes;
    }

    public SkillTypes skillTypes() {
        return skillTypes;
    }
}
