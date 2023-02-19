package app.models;

import dal.api.Model;
import sprouts.Var;

public interface Skill extends Model<Skill>
{
    Var<SkillType> type();
    Var<Integer> level();
    Var<Boolean> isProficient();
    Var<Double> learnability();

}
