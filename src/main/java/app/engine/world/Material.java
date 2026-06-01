package app.engine.world;

/**
 *  The kinds of "stuff" a {@code WorldSection} can be made of.
 *  <p>
 *  A section never stores a single material; instead it stores a mixture of
 *  fractions (see {@link WorldSectionEtherData}), for example "90% air, 5% soil,
 *  5% rock". This mixture drives both rendering (colour, shape, reflectivity)
 *  and the level-of-detail computation, where a parent section averages the
 *  materials of its children into a single representative voxel.
 */
public enum Material
{
    AIR,
    WATER,
    SOIL,
    SAND,
    ROCK,
    WOOD,
    METAL,
    ORGANIC
}