package app.user;

import app.UserContext;

public class CharacterCreatorViewModel
{
    private final CharacterModelsViewModel characterModels;

    public CharacterCreatorViewModel(UserContext user) {
        characterModels = new CharacterModelsViewModel(user);
    }
}
