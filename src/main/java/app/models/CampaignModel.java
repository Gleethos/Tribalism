package app.models;

import dal.api.Model;
import sprouts.Var;
import sprouts.Vars;

/**
 *  A {@code CampaignModel} is the narrative container a game master runs. Its scalar state
 *  (name, description) is aggregated in the {@link Campaign} value; its roster of characters,
 *  NPCs and maps are model relations.
 */
public interface CampaignModel extends Model<CampaignModel>
{
    Var<Campaign> state();
    default Var<String> name()        { return state().zoomTo(Campaign::name, Campaign::withName); }
    default Var<String> description() { return state().zoomTo(Campaign::description, Campaign::withDescription); }

    /** @return The player characters belonging to this campaign. */
    Vars<CharacterModel> characters();
    /** @return The non-player characters (NPCs) the game master controls. */
    Vars<CharacterModel> npcs();
    /** @return The spatial scenes (maps) of this campaign, each backed by a world-engine world. */
    Vars<GameMapModel> maps();
}
