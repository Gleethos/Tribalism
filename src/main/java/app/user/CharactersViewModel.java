package app.user;

import app.UserContext;
import app.models.Character;
import sprouts.Vals;
import sprouts.Vars;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CharactersViewModel
{
    private final List<CharacterViewModel> characters = new ArrayList<>();

    private final Vars<String> characterNames = Vars.of(String.class);

    public CharactersViewModel(UserContext user) {}

    public List<Character> characters() {
        return characters.stream()
                            .map(CharacterViewModel::createCharacter)
                            .filter(Optional::isPresent).map(Optional::get)
                            .toList();
    }


    public Vals<String> characterNames() { return characterNames; }



}
