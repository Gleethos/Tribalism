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


    public boolean isStartServer() { return startServer; }

    public int getServerPort() { return serverPort; }

    public String getDatabaseLocation() { return databaseLocation; }


    @Override
    public void run() {
        var app = createRootViewModel();
        FlatLightLaf.setup();
        UI.show(
            UI.use(EventProcessor.DECOUPLED, () -> new RootView(app))
        );
    }

    public RootViewModel createRootViewModel() {
        AppContext context = new AppContext(this);
        return new RootViewModel(context);
    }

}
