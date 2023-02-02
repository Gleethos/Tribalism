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
        return db.select(User.class).where(User::username).is(username).asList().size() > 0;
    }

    public Optional<User> loginUser(String username) {
        User user = db.select(User.class).where(User::username).is(username).asList().get(0);
        if (user != null) {
            users.add(new UserContext(user));
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public void registerWebUserContext(WebUserContext userContext) {
        //userContext.put()
    }

}
