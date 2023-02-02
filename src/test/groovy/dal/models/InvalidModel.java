package dal.models;

import dal.api.Model;
import sprouts.Var;

public interface InvalidModel extends Model<InvalidModel>
{
    interface Name extends Var<String> {}
    Name name();
    boolean iAmInvalidBecauseIHaveNoImplementation();
}
