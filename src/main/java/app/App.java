package app;

import com.beust.jcommander.Parameter;
import com.formdev.flatlaf.FlatLightLaf;
import swingtree.EventProcessor;
import swingtree.UI;

/**
 *  The start class of the application which simply holds the startup parameters and
 *  starts the application when the {@link Runnable#run()} method is called.
 */
public class App implements Runnable
{
    @Parameter(
        names={"--at"},
        description = "The location of the database file.",
        arity = 1
    )
    private String databaseLocation = "saves/sqlite.db";

    @Parameter(
        names={"--start-server", "-s"},
        description="Start the server when application starts.",
        arity = 1
    )
    private boolean startServer = false;

    @Parameter(
        names={"--server-port", "-p", "--port"},
        description="The port the server should listen on. Default is 8080.",
        arity = 1
    )
    private int serverPort = 8080;

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
    public String getDatabaseLocation() { return databaseLocation; }

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
            while ( true ) { UI.processEvents(); }
    }

    public RootViewModel createRootViewModel() {
        AppContext context = new AppContext(this);
        return new RootViewModel(context);
    }

}
