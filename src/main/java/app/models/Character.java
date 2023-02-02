package app.models;

import dal.api.Model;
import sprouts.Var;

public interface Character extends Model<Character>
{
    Var<CharacterModel> model();
    Var<World> world();
    Var<Player> player();

    Var<String> forename();
    Var<String> surname();
    Var<String> role();
    Var<Integer> age();
    Var<Double> height();
    Var<Double> weight();
    Var<String> description();
    Var<String> image();
}
