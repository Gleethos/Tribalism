package dal.models;

import dal.api.Model;
import sprouts.Var;

public interface Address extends Model<Address>
{
    interface PostalCode extends Var<String> {}
    interface Street extends Var<String> {}
    interface City extends Var<String> {}
    interface Country extends Var<String> {}
    PostalCode postalCode();
    Street street();
    City city();
    Country country();
}
