package dal.values;

import dal.api.Model;
import sprouts.Tuple;
import sprouts.Var;

public interface School extends Model<School>
{
    interface Name extends Var<String> {}
    interface Class extends Var<ClassRoom> {}
    interface Director extends Var<Person> {}
    interface Students extends Var<Tuple<Person>> {}

    Name name();
    Director director();
    Students students();

    Class classRoom1();
    Class classRoom2();
    Class classRoom3();
}
