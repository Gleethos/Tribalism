package dal.values;

import dal.api.Value;

public record FullName(
    String firstName,
    String lastName
) implements Value {
    public FullName {
        if (firstName == null || lastName == null) {
            throw new IllegalArgumentException("First name and last name must not be null");
        }
    }
}
