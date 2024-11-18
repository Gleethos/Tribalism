package dal.models;

import dal.api.Model;
import sprouts.Var;

public interface Book extends Model<Book>
{
    interface Title extends Var<String> {}
    interface Author extends Var<Person> {}
    interface ISBN extends Var<String> {}
    Title title();
    Author author();
    ISBN isbn();
}
