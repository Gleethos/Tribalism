package app;

import swingtree.api.mvvm.Var;

public class User {

    private final Var<String> username;
    private final Var<String> password;
    private final Var<Boolean> isGameMaster;


    public User(String username, String password, boolean isGameMaster) {
        this.username = Var.of(username).withId("username");
        this.password = Var.of(password).withId("password");
        this.isGameMaster = Var.of(isGameMaster).withId("isGameMaster");
    }

    public Var<String> username() { return username; }

    public Var<String> password() { return password; }

    public Var<Boolean> isGameMaster() { return isGameMaster; }

}
