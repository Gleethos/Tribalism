package dal.models;

import dal.api.Model;
import swingtree.api.mvvm.Var;

public interface Ingredient extends Model<Ingredient>
{
    Var<String> name();
    Var<Double> amount();
    Var<String> unit();
}
