package app.models;

import dal.api.Model;
import sprouts.Var;
import sprouts.Vars;

/**
 *  A {@code Campaign} is the narrative container a game master runs: it owns the
 *  roster of player characters and non-player characters, the spatial scenes
 *  ({@link GameMap}s) the story plays out on, and (long term) its sessions and the
 *  rules in force.
 *  <p>
 *  This is the domain concept that used to be called {@code World}. It was renamed to
 *  {@code Campaign} to free the name {@code World} for the spatial simulation in the
 *  world engine ({@code app.engine.world.World}), which a {@link GameMap} is backed by.
 *  See {@code VISION.md} §4 and §5.
 *  <p>
 *  A campaign is the natural unit of <b>export/share</b> and of <b>snapshot</b>
 *  (time-travel), since it bundles a coherent slice of game state.
 */
public interface Campaign extends Model<Campaign>
{
    /** @return The display name of this campaign. */
    Var<String> name();

    /** @return A free-form description / premise of the campaign. */
    Var<String> description();

    /** @return The player characters belonging to this campaign. */
    Vars<Character> characters();

    /** @return The non-player characters (NPCs) the game master controls. */
    Vars<Character> npcs();

    /** @return The spatial scenes (maps) of this campaign, each backed by a world-engine world. */
    Vars<GameMap> maps();
}
