package app.models;

import dal.api.Model;
import sprouts.Var;

public interface AbilityModel extends Model<AbilityModel>
{
    Var<AbilityTypeModel> type();
    Var<Ability> state();
    default Var<Integer> level() { return state().zoomTo(Ability::level, Ability::withLevel); }
}
