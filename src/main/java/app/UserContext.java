package app;

import app.models.User;

/**
 *  This is a compositional sub context of the {@link AppContext} which is responsible for
 *  holding the state of a currently logged-in user.
 *  This includes things like view models and database models.
 */
public final class UserContext
{
    private final User user;

    // TODO: Add more state here, like a list of characters, worlds, etc.

    public UserContext(User user) {
        this.user = user;
    }

    public User user() { return user; }

    /**
     * @return A view model for displaying and interacting with user information.
     */
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
