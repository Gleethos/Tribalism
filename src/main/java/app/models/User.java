package app.models;

import dal.api.Value;

/** The immutable credential state of a {@link UserModel}. */
public record User(String username, String password) implements Value {
    public User {
        if ( username == null || password == null )
            throw new IllegalArgumentException("User fields must not be null");
    }
    public static User empty() { return new User("", ""); }
    public User withUsername(String username) { return new User(username, password); }
    public User withPassword(String password) { return new User(username, password); }
}
