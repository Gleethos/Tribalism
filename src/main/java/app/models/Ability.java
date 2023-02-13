package app.models;

import dal.api.Model;
import sprouts.Var;

public interface Ability extends Model<Ability>
{
    Var<String> name();
    Var<String> description();
    Var<Integer> value();
}
