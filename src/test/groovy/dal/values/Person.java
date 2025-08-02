package dal.values;

import dal.api.Value;

public record Person(
    FullName name,
    int age
) implements Value {
    public Person {
        if (name == null) {
            throw new IllegalArgumentException("Name must not be null");
        }
    }
}
