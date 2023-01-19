package app;

import swingtree.api.mvvm.Var;

import java.util.Optional;

public class CharacterViewModel
{
    private final Var<String> forename;
    private final Var<String> surname;
    private final Var<String> role;
    private final Var<Integer> age;
    private final Var<Double> height;
    private final Var<Double> weight;
    private final Var<String> description;
    private final Var<String> image;


    public CharacterViewModel() {
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
        return Optional.of(new Character(
            forename.get(),
            surname.get(),
            role.get(),
            age.get(),
            height.get(),
            weight.get(),
            description.get(),
            image.get()
        ));
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
