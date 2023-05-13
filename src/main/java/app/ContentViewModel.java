package app;

import app.user.LoginViewModel;
import app.user.RegisterViewModel;
import sprouts.Val;
import sprouts.Var;
import swingtree.api.mvvm.Viewable;

import javax.swing.*;

/**
 *  A simple wrapper view model which represents the main page of the application
 *  which may contain different view models depending on the user's state.
 *  The content is determined dynamically through the "content" property which
 *  is really just a wrapper around any view model implementing the {@link Viewable} interface.
 *  This allows us to switch between different views without having to create a new view model
 *  for each one.
 *  The view is notified of content switches through the observer pattern.
 */
public final class ContentViewModel
{
    private final AppContext context;
    private final Var<Viewable> content = Var.ofNullable(Viewable.class, null).withId("content");


    public ContentViewModel( AppContext context ) {
        this.context = context;
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

    /**
     * @return A view for the swing side of the application.
     */
    public JComponent createView() { return new ContentView(this); }

}
