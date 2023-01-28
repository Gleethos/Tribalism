package dal.models;

import dal.api.Model;
import swingtree.api.mvvm.Var;
import swingtree.api.mvvm.Vars;

public interface Food extends Model<Food>
{
    Var<String> name();
    Var<Double> calories();
    Var<Double> fat();
    Var<Double> carbs();
    Var<Double> protein();
    Vars<Ingredient> ingredients();
}
