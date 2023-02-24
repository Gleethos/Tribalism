package app.models;

import dal.api.Model;
import sprouts.Var;
import sprouts.Vars;

public interface Role extends Model<Role>
{
    Var<String> name();
    Var<String> description();

    /**
     * @return The skill modifiers of this role.
     */
    Vars<Skill> skills();

    /**
     * @return The ability modifiers of this role.
     */
    Vars<Ability> abilities();
}
