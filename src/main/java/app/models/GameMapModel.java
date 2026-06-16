package app.models;

import dal.api.Model;
import sprouts.Var;

/**
 *  A {@code GameMapModel} is a named spatial scene within a {@link CampaignModel}, backed by a
 *  world-engine world. Its generation-parameter state is aggregated in the {@link GameMap} value.
 */
public interface GameMapModel extends Model<GameMapModel>
{
    Var<GameMap> state();
    default Var<String> name()               { return state().zoomTo(GameMap::name, GameMap::withName); }
    default Var<Long> seed()                 { return state().zoomTo(GameMap::seed, GameMap::withSeed); }
    default Var<Double> chunkSize()          { return state().zoomTo(GameMap::chunkSize, GameMap::withChunkSize); }
    default Var<Double> generationDistance() { return state().zoomTo(GameMap::generationDistance, GameMap::withGenerationDistance); }
}
