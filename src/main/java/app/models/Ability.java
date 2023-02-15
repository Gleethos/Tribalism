package app.models;

import dal.api.Model;
import sprouts.Var;

public interface Ability extends Model<Ability>
{
    Var<AbilityType> type();
    Var<Integer> value();
}
