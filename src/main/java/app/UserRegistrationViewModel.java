package app;

import swingtree.api.mvvm.Val;
import swingtree.api.mvvm.Var;

import java.awt.*;

public class UserRegistrationViewModel
{
    public enum Gender { NOT_SELECTED, MALE, FEMALE, OTHER }
    private final Var<String>  username         ;
    private final Var<String>  password         ;
    private final Var<String>  email            ;
    private final Var<String>  feedback         ;
    private final Var<Color>   feedbackColor    ;
    private final Var<Gender>  gender           ;
    private final Var<Boolean> termsAccepted    ;
    private final Var<Boolean> allInputsDisabled;
    private final Var<UserPageViewModel> userPageViewModel; // Only exists if registration was successful


    public UserRegistrationViewModel() {
        this.username          = Var.of("").withId("username").onAct( it -> validateAll() );
        this.password          = Var.of("").withId("password").onAct( it -> validateAll() );
        this.email             = Var.of("").withId("email").onAct( it -> validateAll() );
        this.gender            = Var.of(Gender.NOT_SELECTED).withId("gender").onAct( it -> validateAll() );
        this.termsAccepted     = Var.of(false).withId("termsAccepted").onAct( it -> validateAll() );
        this.feedback          = Var.of("").withId("feedback");
        this.feedbackColor     = Var.of(Color.BLACK).withId("feedbackColor");
        this.allInputsDisabled = Var.of(false).withId("allInputsDisabled");
        this.userPageViewModel = Var.ofNullable(UserPageViewModel.class, null).withId("userPageViewModel");
        validateAll();
    }


    public Var<String> username() { return username; }
    
    public Var<String> password() { return password; }
    
    public Var<String> email() { return email; }
    
    public Val<String> feedback() { return feedback; }
    
    public Val<Color> feedbackColor() { return feedbackColor; }
    
    public Var<Gender> gender() { return gender; }
    
    public Var<Boolean> termsAccepted() { return termsAccepted; }
    
    public Val<Boolean> allInputsDisabled() { return allInputsDisabled; }

    public Val<UserPageViewModel> userPageViewModel() { return userPageViewModel; }


    private String validatePassword() {
        if ( password.get().length() < 8 )
            return "Password must be at least 8 characters long";
        if ( !password.get().matches(".*[A-Z].*") )
            return "Password must contain at least one uppercase letter";
        return "";
    }
    
    private String validateEmail() {
        if ( !email.get().matches(".*@.*") )
            return "Email must contain an @ character";
        return "";
    }
    
    private String validateUsername() {
        if ( username.get().length() < 3 )
            return "Username must be at least 3 characters long";
        return "";
    }
    
    private String validateTerms() {
        if ( !termsAccepted.get() )
            return "You must accept the terms and conditions";
        return "";
    }
    
    private String validateGender() {
        if ( gender.is(Gender.NOT_SELECTED) )
            return "You must select a valid gender";
        return "";
    }
    
    private String generateValidationMessage() {
        String username = validateUsername();
        String password = validatePassword();
        String email    = validateEmail();
        String terms    = validateTerms();
        String gender   = validateGender();
        
        if ( username.isEmpty() && password.isEmpty() && email.isEmpty() && terms.isEmpty() && gender.isEmpty() )
            return "";
        
        return "Please fix the following errors:\n" +
                username + "\n" +
                password + "\n" +
                email + "\n" +
                terms + "\n" +
                gender;
    }
    
    public boolean validateAll() {
        String validationMessage = generateValidationMessage();
        if ( validationMessage.isEmpty() ) {
            feedback.set("All inputs are valid, feel fre to press the submit button!");
            feedbackColor.set(Color.GREEN);
            rebroadcast();
            return true;
        } else {
            feedback.set(validationMessage);
            feedbackColor.set(Color.RED);
            rebroadcast();
            return false;
        }
    }

    private void rebroadcast() {
        // We rebroadcast all properties:
        username.show();
        password.show();
        email.show();
        feedbackColor.show();
        gender.show();
        termsAccepted.show();
        feedback.show();
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
            doRegistration();
            feedback.set("Registration successful!");
            feedbackColor.set(Color.GREEN);
            userPageViewModel.set(new UserPageViewModel());
        } else {
            allInputsDisabled.set(false);
            feedback.set("Registration failed!");
            feedbackColor.set(Color.RED);
            userPageViewModel.set(null);
        }
    }
    
    private void doRegistration() {
        try {
            feedback.set("...connecting to server...");
            Thread.sleep(1000);
            feedback.set("...sending data...");
            Thread.sleep(1000);
            feedback.set("...waiting for response...");
            Thread.sleep(1000);
            feedback.set("...processing response...");
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean successfullyRegistered() {
        return feedback.get().equals("Registration successful!");
    }
    
    public void reset() {
        username.set("");
        password.set("");
        email.set("");
        termsAccepted.set(false);
        gender.set(Gender.NOT_SELECTED);
        feedback.set("");
        feedbackColor.set(Color.BLACK);
        allInputsDisabled.set(false);
        userPageViewModel.set(null);
        validateAll();
    }
}
