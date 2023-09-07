package app.user;

import app.UserContext;
import sprouts.Vars;

import java.util.ArrayList;

public class CharacterModelsViewModel {

    private final Vars<CharacterModelViewModel> characterModels = Vars.of(CharacterModelViewModel.class);

    public CharacterModelsViewModel(UserContext user) {

    }
}
