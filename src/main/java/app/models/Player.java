package app.models;

import dal.api.Model;
import swingtree.api.mvvm.Var;
import swingtree.api.mvvm.Vars;

public interface Player extends Model<Player>
{
    Var<User> identity();
    Var<World> world();
    Vars<CharacterModel> characters();
}
