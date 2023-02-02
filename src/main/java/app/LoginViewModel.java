package app;

import sprouts.Val;
import sprouts.Var;
import swingtree.api.mvvm.Viewable;

import javax.swing.*;

public class LoginViewModel implements Viewable
{
    private final AppContext context;
    private final ContentViewModel contentViewModel;

    private final Var<String> username;
    private final Var<String> password;
    private final Var<String> feedback;
    private final Var<Boolean> usernameIsValid;
    private final Var<Boolean> passwordIsValid;
    private final Var<Boolean> loginButtonEnabled;
    private final Var<Boolean> allInputsDisabled;
    private final Var<Boolean> inputValid;


    public LoginViewModel(AppContext context, ContentViewModel contentViewModel) {
        this.context = context;
        this.contentViewModel = contentViewModel;
        this.username = Var.of("").withId("username").onAct( it -> validate() );
        this.password = Var.of("").withId("password").onAct( it -> validate() );
        this.feedback = Var.of("").withId("feedback");
        this.usernameIsValid = Var.of(false).withId("usernameIsValid");
        this.passwordIsValid = Var.of(false).withId("passwordIsValid");
        this.loginButtonEnabled = Var.of(false).withId("loginButtonEnabled");
        this.allInputsDisabled = Var.of(false).withId("allInputsDisabled");
        this.inputValid = Var.of(false).withId("inputValid");
    }

    public Var<String> username() { return username; }

    public Var<String> password() { return password; }

    public Val<String> feedback() { return feedback; }

    public Val<Boolean> usernameIsValid() { return usernameIsValid; }

    public Val<Boolean> passwordIsValid() { return passwordIsValid; }

    public Val<Boolean> loginButtonEnabled() { return loginButtonEnabled; }

    public Val<Boolean> allInputsDisabled() { return allInputsDisabled; }

    public Val<Boolean> inputValid() { return inputValid; }

    private void validate() {
        /*
            Username should be at least 3 characters long
            Password should be at least 4 characters long
        */
        usernameIsValid.set( username.get().length() >= 3 );
        passwordIsValid.set( password.get().length() >= 4 );
        loginButtonEnabled.set( usernameIsValid.get() && passwordIsValid.get() );
        inputValid.set( usernameIsValid.get() && passwordIsValid.get() );
        if ( !loginButtonEnabled.get() )
            feedback.set("Username and password must be at least 3 and 4 characters long, respectively");
        else
            feedback.set("");
    }

    public void login() {
        allInputsDisabled.set(true);
        if ( context.userExists(username.get()) ) {
            context.loginUser(username.get()).ifPresent(user -> {
                if ( user.password().is(this.password) ) {
                    feedback.set("Login successful!");
                    contentViewModel.login(new UserContext(user));
                } else
                    feedback.set("Wrong password!");
            });
        }
        else
            feedback.set("User does not exist!");
    }

    public void switchToRegister() {
        contentViewModel.showRegister();
    }

    @Override
    public <V> V createView(Class<V> viewType) {
        if ( JComponent.class.isAssignableFrom(viewType) )
            return viewType.cast(new LoginView(this));
        else
            throw new IllegalArgumentException("Unsupported view type: " + viewType);
    }
}
