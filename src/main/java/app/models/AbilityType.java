package app.models;

import dal.api.Model;
import sprouts.Var;

public interface AbilityType extends Model<AbilityType>
{
    Var<String> name();
    Var<String> description();
}
