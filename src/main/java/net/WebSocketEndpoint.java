package net;

import binding.UserContext;

public class WebSocketEndpoint {
    private final UserContext userContext;

    public WebSocketEndpoint(UserContext userContext) {
        this.userContext = userContext;
    }

    //@Override
    //public void configure(WebSocketServletFactory factory) {
    //    // set a 10 second idle timeout
    //    factory.getPolicy().setIdleTimeout(10000);
    //    // register my socket
    //    factory.setCreator((req, res)->{
    //        return new BindingWebSocket(userContext);
    //    });
    //}
}
