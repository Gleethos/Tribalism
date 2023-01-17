package test;

import app.UserRegistrationViewModel;
import binding.UserContext;
import net.BindingWebSocket;
import net.Client;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.util.HtmlUtils;

@Controller
public class GreetingController {

    private final BindingWebSocket bindingWebSocket;
    private final UserContext userContext;
    private final Client client;
    private final String[] sent = new String[1];

    public GreetingController() {
        this.client = new Client() {
            @Override
            public void send(String message) {
                sent[0] = message;
            }

            @Override public void close() {}
            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public String getRemoteAddress() {
                return "localhost";
            }
        };
        this.userContext = new UserContext();
        var vm = new UserRegistrationViewModel();
        this.userContext.put(vm);
        this.bindingWebSocket = new BindingWebSocket(userContext,client);
    }


    @MessageMapping("/hello")
    @SendTo("/topic/greetings")
    public Greeting greeting(HelloMessage message) throws Exception {
        this.bindingWebSocket.onMessage(message.getName());
        return new Greeting(this.sent[0]);
        //return new Greeting("Hello, " + HtmlUtils.htmlEscape(message.getName()) + "!");
    }

}
