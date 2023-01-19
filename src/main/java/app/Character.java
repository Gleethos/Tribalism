package app;

import swingtree.api.mvvm.Val;
import swingtree.api.mvvm.Var;

public class Character
{
    private final Var<String> forename;
    private final Var<String> surname;
    private final Var<String> role;
    private final Var<Integer> age;
    private final Var<Double> height;
    private final Var<Double> weight;
    private final Var<String> description;
    private final Var<String> image;


    public Character(
        String forename,
        String surname,
        String role,
        int age,
        double height,
        double weight,
        String description,
        String image
    ) {
        this.forename    = Var.of(forename).withId("forename");
        this.surname     = Var.of(surname).withId("surname");
        this.role        = Var.of(role).withId("role");
        this.age         = Var.of(age).withId("age");
        this.height      = Var.of(height).withId("height");
        this.weight      = Var.of(weight).withId("weight");
        this.description = Var.of(description).withId("description");
        this.image       = Var.of(image).withId("image");
    }

    public Val<String> forename() { return forename; }

    public Val<String> surname() { return surname; }

    public Val<String> role() { return role; }

    public Val<Integer> age() { return age; }

    public Val<Double> height() { return height; }

    public Val<Double> weight() { return weight; }

    public Val<String> description() { return description; }

    public Val<String> image() { return image; }

    public CharacterViewModel toViewModel() {
        CharacterViewModel viewModel = new CharacterViewModel();
        viewModel.forename().set(forename.get());
        viewModel.surname().set(surname.get());
        viewModel.role().set(role.get());
        viewModel.age().set(age.get());
        viewModel.height().set(height.get());
        viewModel.weight().set(weight.get());
        viewModel.description().set(description.get());
        viewModel.image().set(image.get());
        return viewModel;
    }
}
