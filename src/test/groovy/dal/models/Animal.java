package dal.models;

import dal.api.Model;
import sprouts.Var;

public interface Animal<M> extends Model<M>
{
    Var<String> name();
}
