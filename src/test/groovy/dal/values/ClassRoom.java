package dal.values;

import dal.api.Value;
import sprouts.Tuple;

public record ClassRoom(
    String name,
    int grade,
    Person teacher,
    Tuple<Person> students
) implements Value {
    public ClassRoom {
        if (name == null || teacher == null || students == null) {
            throw new IllegalArgumentException("Name, teacher, and students must not be null");
        }
    }
}
