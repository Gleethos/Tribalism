package app.models;

import dal.api.Model;
import sprouts.Var;

public interface CharacterModel extends Model<CharacterModel>
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
