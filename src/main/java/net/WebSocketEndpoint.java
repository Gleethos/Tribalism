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
            return new BindingWebSocket(userContext);
        });
    }
}
