package app;

import com.beust.jcommander.Parameter;
import com.formdev.flatlaf.FlatLightLaf;
import swingtree.EventProcessor;
import swingtree.UI;

/**
 *  The start class of the application which simply holds the startup parameters and
 *  starts the application when the {@link Runnable#run()} method is called.
 *  We are using the JCommander library to parse the startup parameters
 *  using its annotations (see {@link Parameter}).
 */
public final class App implements Runnable
{
    public static String SAVE_FILE_NAME = "tribalism.db";

    /**
     *  We are using SQLite as the database engine, which stores all of its data in a single file,
     *  in essence it's an application save file.
     *  This parameter is used to
     *  determine the location of the database file.
     *  <p>
     *  An example of using this parameter would be: <br>
     *  <code>java -jar tribalism.jar --at /home/user/myTribalismApp</code>
     */
    @Parameter(
        names={"--at"},
        description = "The location of the database file.",
        arity = 1
    )
    private String databaseLocation = "saves";

    /**
     * The Tribalism application is a hybrid desktop/web application, meaning it can be used
     * both as a desktop application and as a web application.
     * This parameter is used to determine whether the server should be started immediately
     * after application launch or not.
     * <p>
     * An example of using this parameter would be: <br>
     * <code>java -jar tribalism.jar --start-server true</code>
     */
    @Parameter(
        names={"--start-server", "-s"},
        description="Start the server when application starts.",
        arity = 1
    )
    private boolean startServer = false;

    /**
     * The Tribalism application is a hybrid desktop/web application, meaning it can be used
     * both as a desktop application and as a web application.
     * This parameter is used to determine the port onto which the web portal server should
     * listen for clients.
     * <p>
     * An example of using this parameter would be: <br>
     * <code>java -jar tribalism.jar --server-port 8080</code>
     */
    @Parameter(
        names={"--server-port", "-p", "--port"},
        description="The port the server should listen on. Default is 8080.",
        arity = 1
    )
    private int serverPort = 8080;

    /**
     * As a hybrid desktop/web application, Tribalism can be used
     * both as a desktop application and as a web application.
     * This parameter is used to determine whether the application should be launched without
     * desktop UI or not.
     * <p>
     * An example of using this parameter would be: <br>
     * <code>java -jar tribalism.jar --headless true</code>
     */
    @Parameter(
        names={"--headless"},
        description="Run the application in headless mode, meaning no UI will be shown.",
        arity = 1
    )
    private boolean headless = false;


    /**
     * @return True if the server should be started immediately after application launch or not.
     */
    public boolean isStartServer() { return startServer; }

    /**
     * @return The port onto which the web portal server should listen for clients.
     */
    public int getServerPort() { return serverPort; }

    /**
     * @return The path to the file where the application save file should be created/loaded.
     */
    public String getDatabaseLocation() {
        if ( databaseLocation.endsWith(".db") ) {
            // The user not only provided a path but also a filename, we have to remove that:
            return databaseLocation.substring(0, databaseLocation.lastIndexOf('/'));
        }
        return databaseLocation;
    }

    public String getSaveFileName() {
        if ( databaseLocation.endsWith(".db") ) {
            // The user not only provided a path but also a filename, we have to remove that:
            return databaseLocation.substring(databaseLocation.lastIndexOf('/') + 1);
        }
        return SAVE_FILE_NAME;
    }

    /**
     * @return True if the application should be launched without desktop UI.
     */
    public boolean isHeadless() { return headless; }

    @Override
    public void run() {
        var app = createRootViewModel();
        if ( !isHeadless() ) {
            FlatLightLaf.setup();
            UI.show(
                UI.use(EventProcessor.DECOUPLED, () -> new RootView(app))
            );
        }
        else
            UI.joinDecoupledEventProcessor(); // We are using the Swing-Tree event processor!
    }

    /**
     * @return The root view model of the application,
     *          which is the entry point of the application business logic.
     */
    public RootViewModel createRootViewModel() {
        AppContext context = new AppContext(this);
        return new RootViewModel(context);
    }

}
