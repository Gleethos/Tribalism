package app;

import app.models.User;

public class UserContext {

    private final User user;

    public UserContext(User user) {
        this.user = user;
    }

    public User user() { return user; }

    public UserViewModel toViewModel() {
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
