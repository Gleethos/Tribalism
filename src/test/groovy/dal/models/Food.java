package dal.models;

import dal.api.Model;
import sprouts.Var;
import sprouts.Vars;

public interface Food extends Model<Food>
{
    Var<String> name();
    Var<Double> calories();
    Var<Double> fat();
    Var<Double> carbs();
    Var<Double> protein();
    Vars<Ingredient> ingredients();
}
