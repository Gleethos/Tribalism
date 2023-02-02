package app.models;

import dal.api.Model;
import sprouts.Var;
import sprouts.Vars;

public interface Player extends Model<Player>
{
    Var<User> identity();
    Var<World> world();
    Vars<CharacterModel> characters();
}
