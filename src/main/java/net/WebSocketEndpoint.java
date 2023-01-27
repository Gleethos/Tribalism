package net;

import app.AppContext;
import app.ContentViewModel;
import binding.WebUserContext;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketEndpoint extends WebSocketServlet {

    private final AppContext appContext;

    private final Map<String, WebUserContext> userContexts = new ConcurrentHashMap<>();

    public WebSocketEndpoint(AppContext appContext) {
        this.appContext = appContext;
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
            WebUserContext userContext;
            if ( !userContexts.containsKey(session.getId()) ) {
                userContext = new WebUserContext();
                userContext.put(new ContentViewModel(appContext));
                appContext.registerWebUserContext(userContext);
                userContexts.put(session.getId(), userContext);
            }
            else
                userContext = userContexts.get(session.getId());

            return new BindingWebSocket(userContext, session);
        });
    }
}
