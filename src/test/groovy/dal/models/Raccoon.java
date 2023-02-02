package dal.models;

import sprouts.Var;

public interface Raccoon extends Animal<Raccoon>
{
    Var<String> favouriteGarbage();

}
