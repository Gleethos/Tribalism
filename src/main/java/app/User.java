package app;

import swingtree.api.mvvm.Var;

public class User {

    private final Var<String> username;
    private final Var<String> password;


    public User(String username, String password) {
        this.username = Var.of(username).withId("username");
        this.password = Var.of(password).withId("password");
    }

    public Var<String> username() { return username; }

    public Var<String> password() { return password; }

}
