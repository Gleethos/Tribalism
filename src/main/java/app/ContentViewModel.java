package app;

import swingtree.api.mvvm.Val;
import swingtree.api.mvvm.Var;
import swingtree.api.mvvm.Viewable;

import javax.swing.*;

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

    public void showLogin() {
        content.set(new LoginViewModel(context, this));
    }

    public JComponent createView() { return new ContentView(this); }

}
