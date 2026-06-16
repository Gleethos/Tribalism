package app.models;

import dal.api.Model;
import sprouts.Var;

public interface UserModel extends Model<UserModel>
{
    Var<User> state();
    default Var<String> username() { return state().zoomTo(User::username, User::withUsername); }
    default Var<String> password() { return state().zoomTo(User::password, User::withPassword); }
}
