package app;

import app.models.*;
import app.models.bootstrap.ModelTypes;
import dal.api.DataBase;
import dal.api.DataBaseProcessor;
import net.WebUserContext;
import sprouts.Vars;
import swingtree.EventProcessor;

import java.util.List;
import java.util.Optional;

/**
 *  Instances of this class are shared between all users as well as most view models in general.
 *  It is used to access persistent data and to perform operations on it.
 *  So here you can find access to the database, and all current user contexts, etc.
 *  <p>
 *  <b>Note that for every running application there is only one instance of this class.
 *  Please ensure that all methods are thread-safe and the implementation details are both
 *  well encapsulated and well documented.</b>
 */
public final class AppContext
{
    private final App app; // The application configuration
    private final DataBase db;
    private final ModelTypes modelTypes;

    private final Vars<UserContext> users = Vars.of(UserContext.class);



    public AppContext(App app) {
        this.app = app;
        this.db = DataBase.at(app.getDatabaseLocation()+"/"+app.getSaveFileName(), createQueryProcessor());
        this.modelTypes = new ModelTypes(db, app.getDatabaseLocation());
    }

    private DataBaseProcessor createQueryProcessor() {
        var mainThread = Thread.currentThread();
        return new DataBaseProcessor() {
            @Override
            public void process(Runnable task) {
                if ( Thread.currentThread() == mainThread ) {
                    task.run();
                    return;
                }
                EventProcessor.DECOUPLED.registerAppEvent(task);
            }

            @Override
            public void processNow(Runnable task) {
                if ( Thread.currentThread() == mainThread ) {
                    task.run();
                    return;
                }
                EventProcessor.DECOUPLED.registerAndRunAppEventNow(task);
            }

            @Override public List<Thread> getThreads() { return List.of(mainThread); }
        };
    }

    /**
     * Returns the application configuration which contains things like the database location, server port, etc.
     *
     * @return The application configuration.
     */
    public App app() { return app; }

    /**
     * Returns the database which is used to store persistent data, like users, characters, etc.
     *
     * @return The database API, which is from the TopSoil project.
     */
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
        // I don't know if the app context should care about web user contexts, I don't think so. For now.
    }

}
