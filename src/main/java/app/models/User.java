package app.models;

import dal.api.Model;
import swingtree.api.mvvm.Var;

public interface User extends Model<User> {

    interface Username extends Var<String> {}
    interface Password extends Var<String> {}

    Username username();

    Password password();

}
