package app.engine.world;

/**
 *  The kinds of "stuff" a {@code WorldSector} can be made of.
 *  <p>
 *  A sector never stores a single material; instead it stores a mixture of
 *  fractions (see {@link WorldSectorEtherData}), for example "90% air, 5% soil,
 *  5% rock". This mixture drives both rendering (colour, shape, reflectivity)
 *  and the level-of-detail computation, where a parent sector averages the
 *  materials of its children into a single representative voxel.
 */
public enum Material
{
    AIR,
    WATER,
    SOIL,
    GRASS,
    SAND,
    ROCK,
    WOOD,
    METAL,
    ORGANIC
}