package app;

import app.models.Character;
import sprouts.Vals;
import sprouts.Var;
import sprouts.Vars;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserViewModel {

    private final UserContext user;

    private final Var<String> username;

    private final Var<String> password;

    private final List<CharacterViewModel> characters = new ArrayList<>();

    private final Vars<String> characterNames = Vars.of(String.class);

    public UserViewModel(UserContext user) {
        this.user = user;
        this.username = Var.of(user.user().username().get()).withId("username");
        this.password = Var.of(user.user().password().get()).withId("password");
    }


    public List<Character> characters() {
        return characters.stream()
                        .map(CharacterViewModel::createCharacter)
                        .filter(Optional::isPresent).map(Optional::get)
                        .toList();
    }

    public Vals<String> characterNames() { return characterNames; }

    public void safeModifications() {
        user.applyAndSaveNewUserName(username.get());
        user.applyAndSaveNewPassword(password.get());
    }
}
