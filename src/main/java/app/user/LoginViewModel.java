package app.user;

import app.AppContext;
import app.ContentViewModel;
import app.UserContext;
import sprouts.Val;
import sprouts.Var;
import swingtree.api.mvvm.Viewable;

import javax.swing.*;
import java.awt.*;

public class LoginViewModel implements Viewable
{
    private final AppContext context;
    private final ContentViewModel contentViewModel;

    private final Var<String> username;
    private final Var<String> password;
    private final Var<String> feedback;
    private final Var<Boolean> usernameIsValid;
    private final Var<Boolean> passwordIsValid;
    private final Var<Color> usernameBackgroundColor;
    private final Var<Color> passwordBackgroundColor;
    private final Var<Boolean> loginButtonEnabled;
    private final Var<Boolean> textFieldsEnabled;
    private final Var<Boolean> inputValid;
    private final Var<Color> feedbackColor;


    public LoginViewModel(AppContext context, ContentViewModel contentViewModel) {
        this.context = context;
        this.contentViewModel = contentViewModel;
        this.username = Var.of("").withId("username").onAct( it -> validate() );
        this.password = Var.of("").withId("password").onAct( it -> validate() );
        this.feedback = Var.of("").withId("feedback");
        this.usernameIsValid = Var.of(false).withId("usernameIsValid");
        this.passwordIsValid = Var.of(false).withId("passwordIsValid");
        this.usernameBackgroundColor = Var.of(Color.WHITE).withId("usernameBackgroundColor");
        this.passwordBackgroundColor = Var.of(Color.WHITE).withId("passwordBackgroundColor");
        this.loginButtonEnabled = Var.of(false).withId("loginButtonEnabled");
        this.textFieldsEnabled = Var.of(true).withId("textFieldsEnabled");
        this.inputValid = Var.of(false).withId("inputValid");
        this.feedbackColor = Var.of(Color.RED).withId("feedbackColor");
    }

    private void adjustFeedbackStyles() {
        if ( this.usernameIsValid.is(false) && this.username.isNot("") )
            this.usernameBackgroundColor.set(new Color(255, 102, 102));
        else
            this.usernameBackgroundColor.set(Color.WHITE);
        if ( this.passwordIsValid.is(false) && this.password.isNot("") )
            this.passwordBackgroundColor.set(new Color(255, 102, 102));
        else
            this.passwordBackgroundColor.set(Color.WHITE);

        if ( this.inputValid.isNot(true) )
            this.feedbackColor.set(Color.RED);
        else
            this.feedbackColor.set(new Color(0,100,0));
    }

    public Var<String> username() { return username; }

    public Var<String> password() { return password; }

    public Val<String> feedback() { return feedback; }

    public Val<Color> usernameBackgroundColor() { return usernameBackgroundColor; }

    public Val<Color> passwordBackgroundColor() { return passwordBackgroundColor; }

    public Val<Boolean> loginButtonEnabled() { return loginButtonEnabled; }

    public Val<Boolean> textFieldsEnabled() { return textFieldsEnabled; }

    public Val<Color> feedbackColor() { return feedbackColor; }

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

        adjustFeedbackStyles();
    }

    public void login() {
        if ( context.userExists(username.get()) ) {
            context.loginUser(username.get()).ifPresent(user -> {
                if ( user.password().is(this.password) ) {
                    contentViewModel.login(new UserContext(user));
                    feedback.set("Login successful!");
                    feedbackColor.set(new Color(0,100,0));
                    textFieldsEnabled.set(false); // disable all text inputs after successful login
                    loginButtonEnabled.set(false); // disable login button after successful login
                } else {
                    feedback.set("Wrong password!");
                    feedbackColor.set(Color.RED);
                }
            });
        }
        else {
            feedback.set("User does not exist!");
            feedbackColor.set(Color.RED);
        }
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
