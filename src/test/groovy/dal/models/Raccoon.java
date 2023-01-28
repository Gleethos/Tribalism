package dal.models;

import swingtree.api.mvvm.Var;

public interface Raccoon extends Animal<Raccoon>
{
    Var<String> favouriteGarbage();

}
