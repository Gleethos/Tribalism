package app.models;

import dal.api.Model;
import swingtree.api.mvvm.Var;
import swingtree.api.mvvm.Vars;

public interface GameMaster extends Model<GameMaster>
{
    interface Identity extends Var<User> {}

    interface Worlds extends Vars<World> {}

    Identity identity();

    Worlds worlds();

}
