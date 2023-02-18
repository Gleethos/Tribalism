package app.models;

import dal.api.Model;
import sprouts.Var;

public interface SkillType extends Model<SkillType>
{
    Var<String> name();
    Var<String> description();
}
