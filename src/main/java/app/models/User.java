package app.models;

import dal.api.Model;
import swingtree.api.mvvm.Var;

public interface User extends Model<User>
{
    Var<String> username();
    Var<String> password();
}
