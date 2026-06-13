package models.sheet;

import app.models.sheet.CharacterSheet;
import dal.api.Model;
import sprouts.Var;

/**
 *  A minimal Topsoil model used by {@code CharacterSheet_Spec} to round-trip a
 *  {@link CharacterSheet} value tree through the database in isolation from the relational
 *  web of the real {@code Character} model.
 */
public interface SheetHolder extends Model<SheetHolder>
{
    Var<String> name();
    Var<CharacterSheet> sheet();
}
