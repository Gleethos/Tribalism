package app.user;

import app.AppContext;
import app.ContentViewModel;
import app.models.User;
import sprouts.Val;
import sprouts.Var;
import app.ViewModel;

import java.awt.*;

public class RegisterViewModel implements ViewModel
{
    private final AppContext context;
    private final ContentViewModel contentViewModel;

    private final Var<String>  username;
    private final Var<String>  password;
    private final Var<Boolean> usernameIsValid;
    private final Var<Boolean> passwordIsValid;
    private final Var<Color>   usernameBackgroundColor;
    private final Var<Color>   passwordBackgroundColor;
    private final Var<String>  feedback;
    private final Var<Color>   feedbackColor;
    private final Var<Boolean> allInputsDisabled;

    public RegisterViewModel(AppContext context, ContentViewModel contentViewModel) {
        this.context = context;
        this.contentViewModel = contentViewModel;
        this.username          = Var.of("").withId("username").onAct( it -> validateAll() );
        this.password          = Var.of("").withId("password").onAct( it -> validateAll() );
        this.usernameIsValid   = Var.of(false).withId("usernameIsValid");
        this.passwordIsValid   = Var.of(false).withId("passwordIsValid");
        this.usernameBackgroundColor = Var.of(Color.WHITE).withId("usernameBackgroundColor");
        this.passwordBackgroundColor = Var.of(Color.WHITE).withId("passwordBackgroundColor");
        this.feedback          = Var.of("").withId("feedback");
        this.feedbackColor     = Var.of(Color.BLACK).withId("feedbackColor");
        this.allInputsDisabled = Var.of(false).withId("allInputsDisabled");
        validateAll();
    }


    public Var<String> username() { return username; }

    public Var<String> password() { return password; }

    public Val<Color> usernameBackgroundColor() { return usernameBackgroundColor; }

    public Val<Color> passwordBackgroundColor() { return passwordBackgroundColor; }

    public Val<String> feedback() { return feedback; }

    public Val<Color> feedbackColor() { return feedbackColor; }

    public Val<Boolean> allInputsDisabled() { return allInputsDisabled; }


    private String validatePassword() {
        if ( password.get().length() < 3 ) {
            this.passwordIsValid.set(false);
            return "Password must be at least 3 characters long";
        }
        if ( !password.get().matches(".*[A-Z].*") ) {
            this.passwordIsValid.set(false);
            return "Password must contain at least one uppercase letter";
        }
        this.passwordIsValid.set(true);
        return "";
    }

    private String validateUsername() {
        if ( username.get().length() < 3 ) {
            this.usernameIsValid.set(false);
            return "Username must be at least 3 characters long";
        }
        this.usernameIsValid.set(true);
        return "";
    }

    private String generateValidationMessage() {
        String usernameFeedback = validateUsername();
        String passwordFeedback = validatePassword();

        if ( usernameFeedback.isEmpty() && passwordFeedback.isEmpty() )
            return "";

        return "Please fix the following errors:\n" +
                usernameFeedback + "\n" +
                passwordFeedback + "\n";
    }

    public boolean validateAll() {
        String validationMessage = generateValidationMessage();
        boolean isValid;
        if ( validationMessage.isEmpty() ) {
            feedback.set("All inputs are valid, feel fre to press the submit button!");
            feedbackColor.set(Color.GREEN);
            isValid = true;
        } else {
            feedback.set(validationMessage);
            feedbackColor.set(Color.RED);
            isValid = false;
        }
        if ( this.usernameIsValid.is(true) || this.username.is("") )
            usernameBackgroundColor.set(Color.WHITE);
        else
            usernameBackgroundColor.set(new Color(255, 102, 102));
        if ( this.passwordIsValid.is(true) || this.password.is("") )
            passwordBackgroundColor.set(Color.WHITE);
        else
            passwordBackgroundColor.set(new Color(255, 102, 102));

        rebroadcast();
        return isValid;
    }

    private void rebroadcast() {
        // We rebroadcast all properties:
        username.fireSet();
        password.fireSet();
        feedbackColor.fireSet();
        feedback.fireSet();
        usernameBackgroundColor.fireSet();
        passwordBackgroundColor.fireSet();
        /*
            This method is COMPLETELY redundant when we have only one view.
            But if we had multiple views, we would need to rebroadcast all properties
            to all views, so that they can update their state.
         */
    }

    public void register() {
        if ( validateAll() ) {
            if ( userDoesNotYetExist() ) {
                allInputsDisabled.set(true);
                feedbackColor.set(Color.BLACK);
                feedback.set("Registration successful!");
                feedbackColor.set(Color.GREEN);
                try {
                    var user = context.db().create(User.class);
                    user.username().set(username.get());
                    user.password().set(password.get());
                } catch (Exception e) {
                    e.printStackTrace();
                    feedback.set("Registration failed! Cause: " + e.getMessage());
                    allInputsDisabled.set(false);
                    feedbackColor.set(Color.RED);
                }
            } else {
                feedback.set("Registration failed!\nUser '" + username.get() + "' already exists!");
                allInputsDisabled.set(false);
                feedbackColor.set(Color.RED);
            }
        } else {
            allInputsDisabled.set(false);
            feedback.set("Registration failed!");
            feedbackColor.set(Color.RED);
        }
    }

    private boolean userDoesNotYetExist() {
        return context.db()
                .select(User.class)
                .where(User::username)
                .is(this.username)
                .notExists();
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

}
