package app.models;

import dal.api.Model;
import sprouts.Var;

public interface Skill extends Model<Skill>
{
    Var<String> name();
    Var<String> description();
    Var<Integer> value();
}
