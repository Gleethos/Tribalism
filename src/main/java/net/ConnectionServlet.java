package net;

import app.AppContext;
import app.ContentViewModel;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  The {@link ConnectionServlet} establishes a long-lived web-socket
 *  connection between the web-frontend and the server.
 *  Here the web-socket communication is only established, communication happens
 *  in the {@link Connection} class, which is associated with a specific
 *  {@link WebUserContext}.
 */
public class ConnectionServlet extends WebSocketServlet
{
    private final AppContext appContext;

    /**
     *  This map contains all currently active web-user contexts/sessions.
     *  This type of "user" is not necessarily a logged-in user, but rather
     *  a user who has opened the web-frontend in a browser.
     */
    private final Map<String, WebUserContext> userContexts = new ConcurrentHashMap<>();

    public ConnectionServlet(AppContext appContext) {
        this.appContext = appContext;
    }

    /**
     *  This method is called by the Jetty web-server when a new web-socket connection is established.
     * @param factory The factory which is used to create the web-socket.
     */
    @Override
    public void configure(WebSocketServletFactory factory) {
        // set a 1000-second idle timeout (16 minutes)
        factory.getPolicy().setIdleTimeout(1_000_000); 
        // Now we register the socket creator, which establishes a long-lived web-socket based connection
        factory.setCreator((req, res)->{
            /*
                Ok, so a websocket does not really have a session (req.getSession() is null),
                which is a problem, because we want to establish a long-lived connection
                which event persists after the user has reload the page or restarted the browser...
                So what we do is simply use the session from the http request which is
                used to serve the initial React frontend page.
                But how do we get the session from the http request?
                Like so:
            */
            var httpSession = req.getHttpServletRequest().getSession();
            /*
                Now we have a unique session id which we can use to identify the user.
                So what we do is either create a new user context or retrieve an existing one.
            */
            WebUserContext userContext; // This is the user context for a single user!

            // Ok so let's see if we already have a user context for this user:
            if ( !userContexts.containsKey(httpSession.getId()) ) {
                // If not, we create a new one:
                userContext = new WebUserContext();
                userContext.put(new ContentViewModel(appContext));
                appContext.registerWebUserContext(userContext);
                userContexts.put(httpSession.getId(), userContext);
                /*
                    Not that this is actually an anonymous user, meaning that there is
                    not necessarily a user logged in.
                    If the user is logged in, then the web-user-context will receive
                    a user object from the database.
                */
            }
            else
                userContext = userContexts.get(httpSession.getId());

            // Now we return a new web-socket which is bound to the user context:
            return new Connection(userContext, httpSession);
        });
    }
}
