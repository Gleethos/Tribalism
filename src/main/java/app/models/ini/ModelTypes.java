package app.models.ini;

import app.models.*;
import app.models.sheet.AbilityScore;
import app.models.sheet.CharacterSheet;
import app.models.sheet.Identity;
import app.models.sheet.InventoryItem;
import app.models.sheet.SkillScore;
import app.models.sheet.Vitals;
import dal.api.DataBase;
import sprouts.Result;

public class ModelTypes
{
    private final AbilityTypes abilityTypes;
    private final RoleTypes roleTypes;
    private final SkillTypes skillTypes;

    public ModelTypes(dal.api.DataBase db, String workingDirectory) {
        db.createTablesFor(
                // Model interfaces (each backed by an aggregated value where it has scalar state):
                UserModel.class,
                GameMasterModel.class,
                CampaignModel.class,
                GameMapModel.class,
                PlayerModel.class,
                CharacterModel.class,
                AbilityModel.class,
                AbilityTypeModel.class,
                SkillModel.class,
                SkillTypeModel.class,
                RoleModel.class,
                // Aggregated model-state value records:
                User.class,
                Campaign.class,
                GameMap.class,
                Ability.class,
                AbilityType.class,
                Skill.class,
                SkillType.class,
                Role.class,
                // CharacterSheet value tree (the data-oriented sheet representation):
                CharacterSheet.class,
                Identity.class,
                Vitals.class,
                AbilityScore.class,
                SkillScore.class,
                InventoryItem.class
            );
        abilityTypes = new AbilityTypes(db, workingDirectory);
        skillTypes   = new SkillTypes(db, workingDirectory, abilityTypes);
        roleTypes    = new RoleTypes(db, workingDirectory, abilityTypes, skillTypes);
        _verifyLocalWorkingDirConsistency(db);
    }

    private void _verifyLocalWorkingDirConsistency(DataBase db) {
        Result<Boolean> abilitiesMatch = abilityTypes.isDataBaseStateMatchingWorkingDirectory(db);
        Result<Boolean> skillsMatch    = skillTypes.isDataBaseStateMatchingWorkingDirectory(db);
        Result<Boolean> rolesMatch     = roleTypes.isDataBaseStateMatchingWorkingDirectory(db);

        if ( abilitiesMatch.is(false) || skillsMatch.is(false) || rolesMatch.is(false) ) {
            StringBuilder errorMessages = new StringBuilder();
            for ( var problem : abilitiesMatch.problems() ) errorMessages.append(problem).append("\n");
            for ( var problem : skillsMatch.problems() ) errorMessages.append(problem).append("\n");
            for ( var problem : rolesMatch.problems() ) errorMessages.append(problem).append("\n");
            throw new RuntimeException(
                "Local working directory is not consistent with database state!\n\n" +
                errorMessages + "\n"
            );
        }
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

    public void loadFromResources(DataBase db) {
        abilityTypes.loadFromResources(db);
        skillTypes.loadFromResources(db);
        roleTypes.loadFromResources(db);
    }

    public void loadFromWorkingDirectory(DataBase db) {
        abilityTypes.loadFromWorkingDir(db);
        skillTypes.loadFromWorkingDir(db);
        roleTypes.loadFromWorkingDir(db);
    }

    public void saveToWorkingDirectory(DataBase db) {
        abilityTypes.saveToWorkingDir(db);
        skillTypes.saveToWorkingDir(db);
        roleTypes.saveToWorkingDir(db);
    }

    public boolean savesExistInWorkingDirectory() {
        return abilityTypes.localTypesExist() && skillTypes.localTypesExist() && roleTypes.localTypesExist();
    }
}
