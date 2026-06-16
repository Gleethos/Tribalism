package app.campaign;

import app.models.CharacterModel;
import app.models.sheet.CharacterSheet;
import app.user.CharacterSheetViewModel;
import sprouts.Val;

import java.util.Objects;

/**
 *  A small view model for one character in a campaign roster: a display name derived from the
 *  character's sheet, plus the {@link CharacterSheetViewModel} that edits that character's
 *  persisted sheet. Selecting a card in {@link CampaignViewModel} shows its {@link #sheet()}.
 */
public final class CharacterCardViewModel
{
    private final CharacterModel character;
    private final CharacterSheetViewModel sheet;

    public CharacterCardViewModel( CharacterModel character ) {
        this.character = Objects.requireNonNull(character);
        // Defensively ensure the character has a sheet to edit (createCharacter sets one, but a
        // character loaded from older data might not).
        if ( character.sheet().orElseNull() == null )
            character.sheet().set(CharacterSheet.empty());
        // The sheet view model edits the PERSISTED property, so every lens edit round-trips to the db.
        this.sheet = new CharacterSheetViewModel(character.sheet());
    }

    public CharacterModel character() { return character; }

    /** @return The sheet editor for this character (over its persisted sheet property). */
    public CharacterSheetViewModel sheet() { return sheet; }

    /** @return A live display name following the sheet's identity (forename + surname). */
    public Val<String> displayName() {
        return character.sheet().viewAsString(s -> {
            String name = (s.identity().forename() + " " + s.identity().surname()).trim();
            return name.isBlank() ? "Unnamed character" : name;
        });
    }
}
