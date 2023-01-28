package dal.models;

import swingtree.api.mvvm.Var;

public interface Rabbit extends Animal<Rabbit>
{
    Var<String> favouriteCarrot();

}
