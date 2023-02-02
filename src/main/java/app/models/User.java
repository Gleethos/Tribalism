package app.models;

import dal.api.Model;
import sprouts.Var;

public interface User extends Model<User>
{
    Var<String> username();
    Var<String> password();
}
