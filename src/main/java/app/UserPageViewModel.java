package app;

import swingtree.api.mvvm.Var;
import swingtree.api.mvvm.Viewable;

public class UserPageViewModel implements Viewable
{
    private final Var<String> profilepic = Var.of("https://i.imgur.com/1Z1Z1Z1.png").withId("profilepic");
    private final Var<String> username   = Var.of("username").withId("username");
    private final Var<String> email      = Var.of("email").withId("email");
    private final Var<String> bio        = Var.of("bio").withId("bio");
    private final Var<String> location   = Var.of("location").withId("location");
    private final Var<String> website    = Var.of("website").withId("website");
    private final Var<String> feedback   = Var.of("").withId("feedback");

    public Var<String> profilepic() { return profilepic; }

    public Var<String> username() { return username; }

    public Var<String> email() { return email; }

    public Var<String> bio() { return bio; }

    public Var<String> location() { return location; }

    public Var<String> website() { return website; }

    public Var<String> feedback() { return feedback; }

    public void save() {
        feedback.set("Saved!");
    }


    @Override
    public <V> V createView(Class<V> viewType) {
        return (V) new UserPageView(this);
    }
}
