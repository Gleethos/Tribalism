package app;

import binding.WebUserContext;
import swingtree.api.mvvm.Vals;
import swingtree.api.mvvm.Vars;

import java.util.Optional;

public class AppContext
{

    private final Vars<UserContext> users = Vars.of(UserContext.class);

    public AppContext() {

    }

    public Vals<UserContext> users() { return users; }

    public void addUser(UserContext user) {
        users.add(user);
    }

    public boolean userExists(String username) {
        return users.stream().anyMatch(u -> u.user().username().get().equals(username));
    }

    public Optional<User> getUser(String username) {
        return users.stream().map( UserContext::user ).filter(u -> u.username().get().equals(username)).findFirst();
    }

    public void registerWebUserContext(WebUserContext userContext) {
        //userContext.put()
    }

}
