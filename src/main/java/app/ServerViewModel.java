package app;

import net.WebSocketEndpoint;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import sprouts.Val;
import sprouts.Var;

import javax.swing.*;

public class ServerViewModel
{
    public enum Status { ONLINE, OFFLINE, ERROR }

    private final Var<String> portString;
    private final Var<Boolean> portIsValid;
    private final Var<Integer> port;
    private final Var<Status> status;
    private final Var<String> buttonText;
    private final Var<String> statusText;

    private final AppContext context;
    private Server server;

    public ServerViewModel(AppContext context) {
        this.port = Var.of(8080);
        this.portIsValid = Var.of(true);
        this.portString = Var.of("8080").onAct( it -> {
            try {
                port.set(Integer.parseInt(it.get()));
                portIsValid.set(true);
            } catch (NumberFormatException e) {
                port.set(8080);
                portIsValid.set(false);
            }
        });
        this.status = Var.of(Status.OFFLINE);
        this.buttonText = Var.of("Start");
        this.statusText = Var.of("");
        this.context = context;
        this.server = null;
    }

    public Var<String> port() { return portString; }

    public Val<Boolean> portIsValid() { return portIsValid; }

    public Val<Status> status() { return status; }

    public Val<String> buttonText() { return buttonText; }

    public Val<String> statusText() { return statusText; }

    public void buttonClicked() {
        if ( !portIsValid.get() ) {
            statusText.set("Invalid port number");
            return;
        }
        if ( status.is(Status.ONLINE) )
            shutdown();
        else
            start();
    }

    public void shutdown() {
        try {
            server.stop();
        } catch (Exception ex) {
            status.set(Status.ERROR);
            statusText.set(ex.getMessage());
            return;
        }
        server = null;
        status.set(Status.OFFLINE);
        buttonText.set("Start");
        statusText.set("");
    }

    public void start() {
        if ( server != null || status.is(Status.ONLINE) )
            shutdown();

        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port.get());
        server.addConnector(connector);

        // Set up the web socket endpoint
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.setResourceBase("src/main/resources");
        context.getSessionHandler().setMaxInactiveInterval( 60 * 60 * 24 ); // Who plays longer than a day?

        server.setHandler(context);

        ServletHolder holder = new ServletHolder(new WebSocketEndpoint(this.context));
        context.addServlet(holder, "/websocket/*");
        // Now a servlet for serving the actual web page
        // We do this like in spring boot, by adding a default servlet
        // and serving from the static resources' directory *in the build dir*
        context.addServlet(DefaultServlet.class, "/*");
        context.setWelcomeFiles(new String[]{"index.html"});

        try {
            server.start();
        } catch (Throwable t) {
            t.printStackTrace(System.err);
        }

        status.set(Status.ONLINE);
        buttonText.set("Stop");
        statusText.set("...running!");
    }

    public JComponent createView() {
        return new ServerView(this);
    }

}
