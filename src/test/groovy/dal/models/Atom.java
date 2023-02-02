package dal.models;

import dal.api.Model;
import sprouts.Var;

public interface Atom extends Model<Atom>
{
    interface Name extends Var<String> {}
    interface Mass extends Var<Double> {}
    interface AtomicNumber extends Var<Integer> {}
    Name name();
    Mass mass();
    AtomicNumber atomicNumber();
}
