package app;

import app.models.User;
import sprouts.Val;
import sprouts.Var;
import swingtree.api.mvvm.Viewable;

import java.awt.*;

public class RegisterViewModel implements Viewable
{
    private final AppContext context;
    private final ContentViewModel contentViewModel;

    private final Var<String> username         ;
    private final Var<String>  password         ;
    private final Var<Boolean> usernameIsValid;
    private final Var<Boolean> passwordIsValid;
    private final Var<String>  feedback         ;
    private final Var<Color>   feedbackColor    ;
    private final Var<Boolean> allInputsDisabled;
    private final Var<Boolean> inputValid       ;

    public RegisterViewModel(AppContext context, ContentViewModel contentViewModel) {
        this.context = context;
        this.contentViewModel = contentViewModel;
        this.username          = Var.of("").withId("username").onAct( it -> validateAll() );
        this.password          = Var.of("").withId("password").onAct( it -> validateAll() );
        this.usernameIsValid   = Var.of(false).withId("usernameIsValid");
        this.passwordIsValid   = Var.of(false).withId("passwordIsValid");
        this.feedback          = Var.of("").withId("feedback");
        this.feedbackColor     = Var.of(Color.BLACK).withId("feedbackColor");
        this.allInputsDisabled = Var.of(false).withId("allInputsDisabled");
        this.inputValid        = Var.of(false).withId("inputValid");
        validateAll();
    }


    public Var<String> username() { return username; }

    public Var<String> password() { return password; }

    public Val<Boolean> usernameIsValid() { return usernameIsValid; }

    public Val<Boolean> passwordIsValid() { return passwordIsValid; }

    public Val<String> feedback() { return feedback; }

    public Val<Color> feedbackColor() { return feedbackColor; }

    public Val<Boolean> allInputsDisabled() { return allInputsDisabled; }

    public Val<Boolean> inputValid() { return inputValid; }


    private String validatePassword() {
        if ( password.get().length() < 3 ) {
            this.passwordIsValid.set(false);
            this.inputValid.set(false);
            return "Password must be at least 3 characters long";
        }
        if ( !password.get().matches(".*[A-Z].*") ) {
            this.passwordIsValid.set(false);
            this.inputValid.set(false);
            return "Password must contain at least one uppercase letter";
        }
        this.passwordIsValid.set(true);
        return "";
    }

    private String validateUsername() {
        if ( username.get().length() < 3 ) {
            this.usernameIsValid.set(false);
            this.inputValid.set(false);
            return "Username must be at least 3 characters long";
        }
        this.usernameIsValid.set(true);
        return "";
    }

    private String generateValidationMessage() {
        String username = validateUsername();
        String password = validatePassword();

        if ( username.isEmpty() && password.isEmpty() )
            return "";

        return "Please fix the following errors:\n" +
                username + "\n" +
                password + "\n";
    }

    public boolean validateAll() {
        String validationMessage = generateValidationMessage();
        if ( validationMessage.isEmpty() ) {
            feedback.set("All inputs are valid, feel fre to press the submit button!");
            feedbackColor.set(Color.GREEN);
            rebroadcast();
            this.inputValid.set(true);
            return true;
        } else {
            feedback.set(validationMessage);
            feedbackColor.set(Color.RED);
            this.inputValid.set(false);
            rebroadcast();
            return false;
        }
    }

    private void rebroadcast() {
        // We rebroadcast all properties:
        username.fireSet();
        password.fireSet();
        feedbackColor.fireSet();
        feedback.fireSet();
        /*
            This method is COMPLETELY redundant when we have only one view.
            But if we had multiple views, we would need to rebroadcast all properties
            to all views, so that they can update their state.
         */
    }

    public void register() {
        if ( validateAll() ) {
            allInputsDisabled.set(true);
            feedbackColor.set(Color.BLACK);
            feedback.set("Registration successful!");
            feedbackColor.set(Color.GREEN);
            try {
                var user = context.db().create(User.class);
                user.username().set(username.get());
                user.password().set(password.get());
                context.addUser(new UserContext(user));
            } catch (Exception e) {
                e.printStackTrace();
                feedback.set("Registration failed! Cause: " + e.getMessage());
                allInputsDisabled.set(false);
                feedbackColor.set(Color.RED);
            }
        } else {
            allInputsDisabled.set(false);
            feedback.set("Registration failed!");
            feedbackColor.set(Color.RED);
        }
    }

    public void switchToLogin() {
        this.contentViewModel.showLogin();
    }

    public void reset() {
        username.set("");
        password.set("");
        feedback.set("");
        feedbackColor.set(Color.BLACK);
        allInputsDisabled.set(false);
        validateAll();
    }

    @Override
    public <V> V createView(Class<V> viewType) {
        return viewType.cast(new RegisterView(this));
    }

}
