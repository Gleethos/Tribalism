package app;

import app.models.*;
import app.models.Character;
import binding.WebUserContext;
import dal.api.DataBase;
import sprouts.Vals;
import sprouts.Vars;

import java.util.Optional;

/**
 *  Instances of this class are shared between all users as well as most view models in general.
 *  It is used to access persistent data and to perform operations on it.
 *  So here you can find access to the database, and all current user contexts, etc.
 */
public class AppContext
{
    private final App app;
    private final DataBase db;

    private final Vars<UserContext> users = Vars.of(UserContext.class);

    public AppContext(App app) {
        this.app = app;
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

    public App app() { return app; }

    public DataBase db() { return db; }

    public boolean userExists( String username ) {
        return db.select(User.class).where(User::username).is(username).exists();
    }

    public Optional<User> loginUser( String username ) {
        User user = db.select(User.class).where(User::username).is(username).first().orElse(null);
        if ( user != null ) {
            users.add(new UserContext(user));
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public void registerWebUserContext(WebUserContext userContext) {
        //userContext.put()
    }

}
