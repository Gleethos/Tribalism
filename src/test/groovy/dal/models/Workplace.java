package dal.models;

import dal.api.Model;
import sprouts.Var;
import sprouts.Vars;

public interface Workplace extends Model<Workplace>
{
    interface Name extends Var<String> {}
    interface Location extends Var<Address> {}
    interface Employees extends Vars<Person> {}
    Name name();
    Location address();
    Employees employees();
}
