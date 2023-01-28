package app.models;

import swingtree.api.mvvm.Var;

public interface CharacterModel
{
    Var<String> forename();
    Var<String> surname();
    Var<String> role();
    Var<Integer> age();
    Var<Double> height();
    Var<Double> weight();
    Var<String> description();
    Var<String> image();
}
