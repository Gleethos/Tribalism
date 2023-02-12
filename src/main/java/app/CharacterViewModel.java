package app;

import app.models.Character;
import sprouts.Var;

import java.util.Optional;

public final class CharacterViewModel
{
    private final AppContext context;
    private final Var<String> forename;
    private final Var<String> surname;
    private final Var<String> role;
    private final Var<Integer> age;
    private final Var<Double> height;
    private final Var<Double> weight;
    private final Var<String> description;
    private final Var<String> image;


    public CharacterViewModel(AppContext context) {
        this.context = context;
        this.forename     = Var.of("").withId("forename");
        this.surname      = Var.of("").withId("surname");
        this.role         = Var.of("").withId("role");
        this.age          = Var.of(0).withId("age");
        this.height       = Var.of(0.0).withId("height");
        this.weight       = Var.of(0.0).withId("weight");
        this.description  = Var.of("").withId("description");
        this.image        = Var.of("").withId("image");
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
