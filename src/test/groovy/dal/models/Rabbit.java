package dal.models;

import sprouts.Var;

public interface Rabbit extends Animal<Rabbit>
{
    Var<String> favouriteCarrot();

}
