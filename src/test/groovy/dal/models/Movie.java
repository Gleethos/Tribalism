package dal.models;

import dal.api.Model;
import sprouts.Var;

public interface Movie extends Model<Movie>
{
    interface Title extends Var<String> {}
    interface Year extends Var<Integer> {}
    interface Rating extends Var<Double> {}
    Title title();
    Year year();
    Rating rating();
}
