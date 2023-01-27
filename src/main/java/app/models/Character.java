package app.models;

import dal.api.Model;
import swingtree.api.mvvm.Var;

public interface Character extends Model<Character>
{
    interface Forename extends Var<String> {}
    interface Surname extends Var<String> {}
    interface Role extends Var<String> {}
    interface Age extends Var<Integer> {}
    interface Height extends Var<Double> {}
    interface Weight extends Var<Double> {}
    interface Description extends Var<String> {}
    interface Image extends Var<String> {} // The path to the svg image

    Forename forename();

    Surname surname();

    Role role();

    Age age();

    Height height();

    Weight weight();

    Description description();

    Image image();
}
