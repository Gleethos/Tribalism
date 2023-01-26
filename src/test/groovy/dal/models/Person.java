package dal.models;

import dal.api.Model;
import swingtree.api.mvvm.Var;

public interface Person extends Model<Person> {

    interface FirstName extends Var<String> {}
    interface LastName extends Var<String> {}
    interface Address extends Var<dal.models.Address> {}

    FirstName firstName();

    LastName lastName();

    Address address();

}
