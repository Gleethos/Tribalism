package dal.models;

import dal.Model;
import swingtree.api.mvvm.Var;
import swingtree.api.mvvm.Vars;

public interface Workplace extends Model<Workplace> {

    interface Name extends Var<String> {}
    interface Location extends Var<Address> {}
    interface Employees extends Vars<Person> {}

    Name name();

    Location address();

    Employees employees();

}
