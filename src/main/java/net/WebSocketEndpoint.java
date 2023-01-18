package net;

import binding.UserContext;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

public class WebSocketEndpoint extends WebSocketServlet {

    private final UserContext userContext;

    public WebSocketEndpoint(UserContext userContext) {
        this.userContext = userContext;
    }

    @Override
    public void configure(WebSocketServletFactory factory) {
        // set a 10 second idle timeout
        factory.getPolicy().setIdleTimeout(10000);
        // register my socket
        factory.setCreator((req, res)->{
            /*
                Ok, so a websocket does not really have a session (req.gtSession() is null),
                but we can fake it by
                using the session from the http request.
                But how do we get the session from the http request?
                Like so:
             */
            var session = req.getHttpServletRequest().getSession();
            return new BindingWebSocket(userContext, session);
        });
    }
}
