package app.models;

import swingtree.api.mvvm.Var;
import swingtree.api.mvvm.Vars;

public interface Player
{
    Var<User> identity();
    Var<World> world();
    Vars<CharacterModel> characters();
}
