package app.models;

import dal.api.Model;
import sprouts.Var;
import sprouts.Vars;

public interface World extends Model<World>
{
    Var<String> name();

    Vars<Character> characters();

    Vars<Character> npcs();

}
