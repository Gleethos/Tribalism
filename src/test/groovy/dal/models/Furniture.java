package dal.models;

import dal.api.Model;
import sprouts.Var;

public interface Furniture extends Model<Furniture>
{
    Var<String> name();
    Var<String> material();
    Var<Double> price();
    Var<Integer> quantity();
    Var<String> category();
    Var<String> color();
}
