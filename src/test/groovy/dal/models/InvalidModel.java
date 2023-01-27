package dal.models;

import dal.api.Model;
import swingtree.api.mvvm.Var;

public interface InvalidModel extends Model<InvalidModel>
{
    interface Name extends Var<String> {}
    Name name();
    boolean iAmInvalidBecauseIHaveNoImplementation();
}
