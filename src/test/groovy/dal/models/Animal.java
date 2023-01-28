package dal.models;

import dal.api.Model;
import swingtree.api.mvvm.Var;

public interface Animal<M> extends Model<M>
{
    Var<String> name();
}
