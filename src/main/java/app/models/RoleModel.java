package app.models;

import dal.api.Model;
import sprouts.Var;
import sprouts.Vars;

public interface RoleModel extends Model<RoleModel>
{
    Var<Role> state();
    default Var<String> name()        { return state().zoomTo(Role::name, Role::withName); }
    default Var<String> description() { return state().zoomTo(Role::description, Role::withDescription); }

    /** @return The skill modifiers of this role. */
    Vars<SkillModel> skills();
    /** @return The ability modifiers of this role. */
    Vars<AbilityModel> abilities();
}
