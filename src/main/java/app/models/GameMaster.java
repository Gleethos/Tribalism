package app.models;

import dal.api.Model;
import sprouts.Var;
import sprouts.Vars;

public interface GameMaster extends Model<GameMaster>
{
    Var<User> identity();

    Vars<World> worlds();
}
