package app.models;

import dal.api.Model;
import sprouts.Var;

public interface SkillModel extends Model<SkillModel>
{
    Var<SkillTypeModel> type();
    Var<Skill> state();
    default Var<Integer> level()        { return state().zoomTo(Skill::level, Skill::withLevel); }
    default Var<Boolean> isProficient() { return state().zoomTo(Skill::isProficient, Skill::withIsProficient); }
    default Var<Double> learnability()  { return state().zoomTo(Skill::learnability, Skill::withLearnability); }
}
