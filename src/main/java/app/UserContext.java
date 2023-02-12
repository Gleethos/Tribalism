package app;

import app.models.User;

/**
 *  This is a compositional sub context of the {@link AppContext} which is responsible for
 *  holding the state of a currently logged-in user.
 *  This includes things like view models and database models.
 */
public final class UserContext {

    private final User user;

    public UserContext(User user) {
        this.user = user;
    }

    public User user() { return user; }

    public UserViewModel getViewModel() {
        UserViewModel viewModel = new UserViewModel(this);
        return viewModel;
    }

    public void applyAndSaveNewUserName(String newUserName) {
        user.username().set(newUserName);
    }

    public void applyAndSaveNewPassword(String newPassword) {
        user.password().set(newPassword);
    }

}
