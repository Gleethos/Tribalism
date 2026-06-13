package app.models;

import sprouts.Var;

public interface Character extends AbstractCharacter<Character>
{
    Var<CharacterModel> model();
    Var<Campaign> campaign();
    Var<Player> player();
}
