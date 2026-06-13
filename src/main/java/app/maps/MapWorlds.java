package app.maps;

import app.engine.world.World;
import app.engine.world.gen.WorldGenerator;
import app.models.GameMap;

/**
 *  The bridge that turns a {@link GameMap} domain model into a live world-engine {@link World}
 *  — the concrete realization of the campaign↔engine seam described in {@code VISION.md} §6.
 *  <p>
 *  A {@code GameMap} stores only the cheap, deterministic <em>recipe</em> for its terrain
 *  (seed plus generation parameters); the heavy spatial simulation is reconstructed on demand
 *  here. Because pristine terrain is a pure function of the seed, a map needs to persist no
 *  voxels until it carries edits that cannot be reproduced from the seed (a later step — see
 *  {@code VISION.md} §6.2/§8 and {@code ARCHITECTURE.md} §10).
 */
public final class MapWorlds
{
    private MapWorlds() {}

    /**
     *  Builds the {@link WorldGenerator} described by a map's recipe, falling back to the
     *  generator's own sensible defaults for any parameter the map leaves unset.
     *
     *  @param map The map whose recipe to read.
     *  @return A generator seeded and configured from the map.
     */
    public static WorldGenerator generatorOf( GameMap map ) {
        long seed = orDefault(map.seed().orElseNull(), 0L);
        WorldGenerator generator = WorldGenerator.withSeed(seed);

        Double chunkSize = map.chunkSize().orElseNull();
        if ( chunkSize != null && chunkSize > 0 )
            generator = generator.withChunkSize(chunkSize);

        Double generationDistance = map.generationDistance().orElseNull();
        if ( generationDistance != null && generationDistance > 0 )
            generator = generator.withGenerationDistance(generationDistance);

        return generator;
    }

    /**
     *  Builds an infinite, generator-backed {@link World} for a map. The returned world starts
     *  as one coarse sector at the origin and streams terrain in around cameras as it is
     *  {@link World#update updated} (see {@link World#of(WorldGenerator)}).
     *
     *  @param map The map to realize.
     *  @return A live engine world generating this map's terrain.
     */
    public static World worldOf( GameMap map ) {
        return World.of(generatorOf(map));
    }

    private static long orDefault( Long value, long fallback ) {
        return value == null ? fallback : value;
    }
}
