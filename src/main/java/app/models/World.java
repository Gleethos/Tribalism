package app.models;

import dal.api.Model;
import swingtree.api.mvvm.Var;
import swingtree.api.mvvm.Vars;

public interface World extends Model<World> {

    interface Name extends Var<String> {}
    interface Characters extends Vars<Character> {}
    interface NPCs extends Vars<Character> {}

    Name name();

    Characters characters();

    NPCs npcs();

}
