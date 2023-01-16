import app.UserRegistrationView;
import app.UserRegistrationViewModel;
import binding.UserContext;
import net.WebSocketEndpoint;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import swingtree.UI;

public class Main {

    public static void main( String... args )
    {

        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(8080);
        server.addConnector(connector);

        // Set up the web socket endpoint
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.setResourceBase("src/main/resources");

        server.setHandler(context);

        var state = new UserContext();
        var vm = new UserRegistrationViewModel();
        state.put(vm);
        ServletHolder holder = new ServletHolder(new WebSocketEndpoint(state));
        context.addServlet(holder, "/websocket/*");
        // Now a servlet for serving the actual web page
        context.addServlet(DefaultServlet.class, "/*");
        context.setWelcomeFiles(new String[]{"test.html"});

        try {
            server.start();
            UI.show(new UserRegistrationView(vm));
            server.join();
        } catch (Throwable t) {
            t.printStackTrace(System.err);
        }
    }

}
