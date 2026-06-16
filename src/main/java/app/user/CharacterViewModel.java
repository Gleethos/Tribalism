package app.user;

import app.AppContext;
import app.models.CharacterModel;
import app.models.sheet.CharacterSheet;
import app.models.sheet.Identity;
import sprouts.Var;

import java.util.Optional;

/**
 *  This is used as a
 */
public final class CharacterViewModel
{
    private final AppContext context;
    private final CharacterModel character;

    private final Var<String> forename;
    private final Var<String> surname;
    private final Var<String> role;
    private final Var<Integer> age;
    private final Var<Double> height;
    private final Var<Double> weight;
    private final Var<String> description;
    private final Var<String> image;


    public CharacterViewModel(AppContext context, CharacterModel character) {
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

    public Optional<CharacterModel> createCharacter() {
        if ( forename.get().isEmpty() || surname.get().isEmpty() ) {
            return Optional.empty();
        }
        var character = this.context.db().create(CharacterModel.class);
        // The character's descriptive scalars now live in the CharacterSheet's Identity value.
        character.sheet().set(
            CharacterSheet.empty().withIdentity(new Identity(
                forename.get(), surname.get(), role.get(), age.get(),
                height.get(), weight.get(), description.get(), image.get()
            ))
        );
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
