package app.models;

import dal.api.Model;
import swingtree.api.mvvm.Var;
import swingtree.api.mvvm.Vars;

public interface World extends Model<World>
{
    Var<String> name();

    Vars<Character> characters();

    Vars<Character> npcs();

}
