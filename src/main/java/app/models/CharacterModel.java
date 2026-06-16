package app.models;

import app.models.sheet.CharacterSheet;
import dal.api.Model;
import sprouts.Var;

/**
 *  A player- or non-player character. Its entire rules-bearing state is the single immutable
 *  {@link CharacterSheet} value tree (identity, vitals, abilities, skills, inventory, notes);
 *  its campaign and owning player are model relations.
 */
public interface CharacterModel extends Model<CharacterModel>
{
    Var<CharacterSheet> sheet();
    Var<CampaignModel> campaign();
    Var<PlayerModel> player();
}
