package app;

import sprouts.Val;
import sprouts.Var;
import swingtree.api.mvvm.Viewable;

import javax.swing.*;

/**
 *  A basic view model which represents the main page of the application.
 *  The content is determined dynamically through the "content" property which
 *  is really just a wrapper around any view model implementing the {@link Viewable} interface.
 *  This allows us to switch between different views without having to create a new view model
 *  for each one.
 *  The view is notified of content switches through the observer pattern.
 */
public class ContentViewModel {

    private final AppContext context;
    private final Var<Viewable> content = Var.ofNullable(Viewable.class, null).withId("content");


    public ContentViewModel(AppContext context) {
        this.context = context != null ? context : new AppContext();
        showLogin();
    }

    public Val<? extends Viewable> content() { return content; }

    public void showRegister() {
        content.set(new RegisterViewModel(context, this));
    }

    public void login(UserContext userContext) {
        content.set(userContext.getViewModel());
    }


    public void showLogin() {
        content.set(new LoginViewModel(context, this));
    }

    public JComponent createView() { return new ContentView(this); }

}
