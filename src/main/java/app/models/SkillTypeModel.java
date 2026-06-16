package app.models;

import dal.api.Model;
import sprouts.Var;

public interface SkillTypeModel extends Model<SkillTypeModel>
{
    Var<SkillType> state();
    default Var<String> name()             { return state().zoomTo(SkillType::name, SkillType::withName); }
    default Var<String> description()      { return state().zoomTo(SkillType::description, SkillType::withDescription); }
    default Var<String> primaryAbility()   { return state().zoomTo(SkillType::primaryAbility, SkillType::withPrimaryAbility); }
    default Var<String> secondaryAbility() { return state().zoomTo(SkillType::secondaryAbility, SkillType::withSecondaryAbility); }
    default Var<String> tertiaryAbility()  { return state().zoomTo(SkillType::tertiaryAbility, SkillType::withTertiaryAbility); }
}
