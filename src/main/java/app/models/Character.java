package app.models;

import sprouts.Var;

public interface Character extends AbstractCharacter<Character>
{
    Var<CharacterModel> model();
    Var<World> world();
    Var<Player> player();
}
