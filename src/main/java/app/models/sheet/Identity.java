package app.models.sheet;

import dal.api.Value;

/**
 *  The descriptive identity of a character: who they are, independent of the rules.
 *  An immutable {@link Value} component of a {@link CharacterSheet}.
 *  <p>
 *  The {@code role} is referenced by name (a {@code RoleType}/{@code Role} name in the
 *  campaign's registry), keeping the sheet self-contained and content-addressable rather
 *  than carrying a mutable model reference.
 */
public record Identity(
    String forename,
    String surname,
    String role,
    int    age,
    double height,
    double weight,
    String description,
    String image
) implements Value {

    public Identity {
        if ( forename == null || surname == null || role == null
          || description == null || image == null ) {
            throw new IllegalArgumentException("Identity string fields must not be null");
        }
    }

    /** @return An empty identity with blank strings and zeroed numbers. */
    public static Identity empty() {
        return new Identity("", "", "", 0, 0.0, 0.0, "", "");
    }

    public Identity withForename(String forename)       { return new Identity(forename, surname, role, age, height, weight, description, image); }
    public Identity withSurname(String surname)         { return new Identity(forename, surname, role, age, height, weight, description, image); }
    public Identity withRole(String role)               { return new Identity(forename, surname, role, age, height, weight, description, image); }
    public Identity withAge(int age)                    { return new Identity(forename, surname, role, age, height, weight, description, image); }
    public Identity withHeight(double height)           { return new Identity(forename, surname, role, age, height, weight, description, image); }
    public Identity withWeight(double weight)           { return new Identity(forename, surname, role, age, height, weight, description, image); }
    public Identity withDescription(String description) { return new Identity(forename, surname, role, age, height, weight, description, image); }
    public Identity withImage(String image)             { return new Identity(forename, surname, role, age, height, weight, description, image); }
}
