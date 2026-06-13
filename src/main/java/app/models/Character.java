package app.models;

import app.models.sheet.CharacterSheet;
import sprouts.Var;

public interface Character extends AbstractCharacter<Character>
{
    Var<CharacterModel> model();
    Var<Campaign> campaign();
    Var<Player> player();

    /**
     *  The rules-bearing state of this character as one immutable {@link CharacterSheet}
     *  value tree (identity, vitals, abilities, skills, inventory, notes).
     *  This is the canonical, data-oriented representation; the flat fields inherited from
     *  {@link AbstractCharacter} are transitional and will be folded into the sheet as the
     *  character view models migrate (see {@code VISION.md} §5.3).
     *
     * @return The character's sheet value.
     */
    Var<CharacterSheet> sheet();
}
