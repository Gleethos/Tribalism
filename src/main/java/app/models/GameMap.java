package app.models;

import dal.api.Model;
import sprouts.Var;

/**
 *  A {@code GameMap} is a named spatial scene within a {@link Campaign} — the tavern,
 *  a dungeon level, the overworld — backed by a world-engine world
 *  ({@code app.engine.world.World}). It is the seam between the domain model and the
 *  engine (see {@code VISION.md} §6).
 *  <p>
 *  The class is named {@code GameMap} rather than {@code Map} to avoid the constant
 *  collision with {@link java.util.Map}; the domain concept is simply "map".
 *  <p>
 *  Pristine engine terrain is a deterministic function of its generation parameters, so
 *  a map persists cheaply as its <b>seed + generation parameters</b>; only player/AI
 *  <b>edits</b> that cannot be reproduced from the seed need to be stored as a diff
 *  (a later step — see {@code VISION.md} §6.2/§8 and {@code ARCHITECTURE.md} §10). For
 *  now this model carries the generation parameters; the serialized edit diff is a
 *  forthcoming field.
 */
public interface GameMap extends Model<GameMap>
{
    /** @return The display name of this map. */
    Var<String> name();

    /** @return The seed driving deterministic procedural generation of the terrain. */
    Var<Long> seed();

    /** @return The world-space edge length of one generated chunk (engine {@code chunkSize}). */
    Var<Double> chunkSize();

    /** @return The full-detail generation radius around a camera (engine {@code generationDistance}). */
    Var<Double> generationDistance();
}
