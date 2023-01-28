package app.models;

import dal.api.Model;
import swingtree.api.mvvm.Var;
import swingtree.api.mvvm.Vars;

public interface GameMaster extends Model<GameMaster>
{
    Var<User> identity();

    Vars<World> worlds();
}
