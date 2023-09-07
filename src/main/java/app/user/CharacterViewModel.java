package app.user;

import app.AppContext;
import app.models.Character;
import sprouts.Var;

import java.util.Optional;

/**
 *  This is used as a
 */
public final class CharacterViewModel
{
    private final AppContext context;
    private final Character character;

    private final Var<String> forename;
    private final Var<String> surname;
    private final Var<String> role;
    private final Var<Integer> age;
    private final Var<Double> height;
    private final Var<Double> weight;
    private final Var<String> description;
    private final Var<String> image;


    public CharacterViewModel(AppContext context, Character character) {
        this.context = context;
        this.character = character;
        this.forename     = Var.of("");
        this.surname      = Var.of("");
        this.role         = Var.of("");
        this.age          = Var.of(0);
        this.height       = Var.of(0.0);
        this.weight       = Var.of(0.0);
        this.description  = Var.of("");
        this.image        = Var.of("");
    }

    public Optional<Character> createCharacter() {
        if ( forename.get().isEmpty() || surname.get().isEmpty() ) {
            return Optional.empty();
        }
        var character = this.context.db().create(Character.class);
        character.forename().set(forename.get());
        character.surname().set(surname.get());
        character.role().set(role.get());
        character.age().set(age.get());
        character.height().set(height.get());
        character.weight().set(weight.get());
        character.description().set(description.get());
        character.image().set(image.get());
        return Optional.of(character);
    }


    public Var<String> forename() { return forename; }

    public Var<String> surname() { return surname; }

    public Var<String> role() { return role; }

    public Var<Integer> age() { return age; }

    public Var<Double> height() { return height; }

    public Var<Double> weight() { return weight; }

    public Var<String> description() { return description; }

    public Var<String> image() { return image; }

}
