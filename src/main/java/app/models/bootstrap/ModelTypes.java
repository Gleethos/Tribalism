package app.models.bootstrap;

import app.models.*;
import app.models.Character;

public class ModelTypes
{
    private static ModelTypes _INSTANCE = null;

    private final AbilityTypes abilityTypes;
    private final RoleTypes roleTypes;
    private final SkillTypes skillTypes;

    public static ModelTypes load(dal.api.DataBase db) {
        if (_INSTANCE != null) return _INSTANCE;
        _INSTANCE = new ModelTypes(db);
        return _INSTANCE;
    }

    private ModelTypes(dal.api.DataBase db) {
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
        abilityTypes = AbilityTypes.load(db);
        roleTypes = RoleTypes.load(db);
        skillTypes = SkillTypes.load(db);
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
