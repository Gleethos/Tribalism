package dal.models;

import dal.api.Model;
import dal.values.Address;
import dal.values.User;
import sprouts.Var;

/**
 *  A model whose state is a single {@link User} value, which in turn holds an {@link Address}
 *  value. The zoom lenses below reach one and two levels deep, exercising recursive zoom
 *  resolution in the query API.
 */
public interface AccountModel extends Model<AccountModel> {

    Var<User> user();

    // One level deep: into the User value.
    default Var<String> username() {
        return user().zoomTo(User::username, User::withUsername);
    }

    // Two levels deep: User -> Address -> postalCode / city.
    default Var<String> postalCode() {
        return user().zoomTo(User::address,    User::withAddress)
                     .zoomTo(Address::postalCode, Address::withPostalCode);
    }
    default Var<String> city() {
        return user().zoomTo(User::address, User::withAddress)
                     .zoomTo(Address::city, Address::withCity);
    }
}
