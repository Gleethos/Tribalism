package app;

import app.models.*;
import app.models.Character;
import binding.WebUserContext;
import dal.api.DataBase;
import sprouts.Vals;
import sprouts.Vars;

import java.util.Optional;

public class AppContext
{
    private final DataBase db;

    private final Vars<UserContext> users = Vars.of(UserContext.class);

    public AppContext() {
        this.db = DataBase.at("saves/sqlite.db");
        this.db.createTablesFor(
            Character.class,
            User.class,
            GameMaster.class,
            World.class,
            Player.class,
            CharacterModel.class
        );
    }

    public DataBase db() { return db; }

    public Vals<UserContext> users() { return users; }

    public void addUser(UserContext user) {
        users.add(user);
    }

    public boolean userExists(String username) {
        return users.stream().anyMatch(u -> u.user().username().get().equals(username));
    }

    public Optional<User> getUser(String username) {
        return users.stream().map( UserContext::user ).filter(u -> u.username().get().equals(username)).findFirst();
    }

    public void registerWebUserContext(WebUserContext userContext) {
        //userContext.put()
    }

}
