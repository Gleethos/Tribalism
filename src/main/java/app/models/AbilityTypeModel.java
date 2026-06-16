package app.models;

import dal.api.Model;
import sprouts.Var;

public interface AbilityTypeModel extends Model<AbilityTypeModel>
{
    Var<AbilityType> state();
    default Var<String> name()        { return state().zoomTo(AbilityType::name, AbilityType::withName); }
    default Var<String> description() { return state().zoomTo(AbilityType::description, AbilityType::withDescription); }
}
