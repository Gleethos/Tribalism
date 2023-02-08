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
        names={"--start-server", "-s"},
        description="Start the server when application starts."
    )
    private boolean startServer = false;

    @Parameter(
        names={"--server-port", "-p"},
        description="The port the server should listen on. Default is 8080."
    )
    private int serverPort = 8080;
    
    public boolean isStartServer() { return startServer; }

    public int getServerPort() { return serverPort; }

    @Override
    public void run() {
        AppContext context = new AppContext(this);
        RootViewModel app = new RootViewModel(context);
        FlatLightLaf.setup();
        UI.show(
            UI.use(EventProcessor.DECOUPLED, () -> new RootView(app))
        );
    }

}
