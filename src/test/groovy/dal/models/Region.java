package dal.models;

import dal.api.Model;
import sprouts.Var;

import java.time.Month;

public interface Region extends Model<Region>
{
    Var<Month> warmestMonth();
    Var<Month> coldestMonth();
    Var<String> name();
}
