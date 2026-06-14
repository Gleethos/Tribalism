package dal.values;

import dal.api.Value;

/**
 *  A {@link Value} that itself holds another value ({@link Address}). Used to demonstrate
 *  recursive, multi-level zoom querying through a chain of nested values.
 */
public record User(
    String username,
    Address address
) implements Value {
    public User withUsername( String username ) { return new User(username, address); }
    public User withAddress( Address address ) { return new User(username, address); }
}
