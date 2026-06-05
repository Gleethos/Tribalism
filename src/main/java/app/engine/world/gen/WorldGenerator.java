package app.engine.world.gen;

import app.engine.primitives.BoundsF64;
import app.engine.primitives.VecF64;
import app.engine.world.Material;
import app.engine.world.WorldSector;
import app.engine.world.WorldSectorEtherData;
import app.engine.world.WorldTreeNode;
import sprouts.Tuple;

import java.util.HashMap;
import java.util.Map;

/**
 *  Procedurally generates a {@link WorldSector} tree from 3D noise.
 *  <p>
 *  The landscape is a height field driven by fractal {@link PerlinNoise}: rock
 *  deep down, soil above it, a thin band of grass at the surface, then air, with
 *  3D noise carving caves below ground and water filling low air pockets.
 *  <p>
 *  Crucially, generation is <i>adaptive</i>: a region is only subdivided into
 *  {@value WorldTreeNode#SECTOR_COUNT} children if it is not homogeneous (its
 *  sample points disagree on material). Large stretches of pure rock or pure air
 *  collapse into a single leaf voxel, so detail naturally concentrates around
 *  surfaces &mdash; which is the whole point of the tree.
 *
 *  @param noise        The seeded noise source.
 *  @param seaLevel     The world-space height below which air pockets fill with water.
 *  @param surfaceLevel The base world-space height of the terrain surface.
 *  @param amplitude    How far the surface rises and falls around {@code surfaceLevel}.
 *  @param frequency    The horizontal scale of the terrain (smaller = broader hills).
 *  @param soilDepth    How far below the surface remains soil before turning to rock.
 *  @param grassDepth   The thickness of the grass band at the very surface.
 *  @param caveThreshold Cave noise above this carves out air; {@code >= 1} disables caves.
 *  @param generationDistance How close (in world units) a camera must be to a region for
 *                            {@link app.engine.world.World} to generate it around the camera.
 *  @param detailDepth  How many levels {@link #generate(BoundsF64)} subdivides a region by default.
 *  @param chunkSize    The world-space edge length of one generated chunk &mdash; the unit
 *                      the {@link app.engine.world.World} streams terrain in and out by.
 */
public record WorldGenerator(
    PerlinNoise noise,
    double seaLevel,
    double surfaceLevel,
    double amplitude,
    double frequency,
    double soilDepth,
    double grassDepth,
    double caveThreshold,
    double generationDistance,
    int detailDepth,
    double chunkSize
) {
    /** @return A generator with sensible defaults for the given {@code seed}. */
    public static WorldGenerator withSeed( long seed ) {
        return new WorldGenerator(
                new PerlinNoise(seed),
                /* seaLevel           */ -8,
                /* surfaceLevel       */  0,
                /* amplitude          */ 24,
                /* frequency          */  0.015,
                /* soilDepth          */  6,
                /* grassDepth         */  1.5,
                /* caveThreshold      */  0.65,
                /* generationDistance */ 96,
                /* detailDepth        */  2,
                /* chunkSize          */ 64
            );
    }

    /** @return This generator configured to build the world {@code distance} units around cameras. */
    public WorldGenerator withGenerationDistance( double distance ) {
        return new WorldGenerator(noise, seaLevel, surfaceLevel, amplitude, frequency, soilDepth, grassDepth, caveThreshold, distance, detailDepth, chunkSize);
    }

    /** @return This generator configured to subdivide a region {@code depth} levels by default. */
    public WorldGenerator withDetailDepth( int depth ) {
        return new WorldGenerator(noise, seaLevel, surfaceLevel, amplitude, frequency, soilDepth, grassDepth, caveThreshold, generationDistance, depth, chunkSize);
    }

    /** @return This generator configured to stream the world in {@code size}-unit chunks. */
    public WorldGenerator withChunkSize( double size ) {
        return new WorldGenerator(noise, seaLevel, surfaceLevel, amplitude, frequency, soilDepth, grassDepth, caveThreshold, generationDistance, detailDepth, size);
    }

    /** @return The sector covering {@code bounds}, generated to this generator's {@link #detailDepth()}. */
    public WorldSector generate( BoundsF64 bounds ) {
        return generate(bounds, detailDepth);
    }

    /** @return The terrain surface height at world coordinates {@code (x, z)}. */
    public double surfaceHeightAt( double x, double z ) {
        double h = noise.fbm(x * frequency, 0, z * frequency, 5, 0.5, 2.0);
        return surfaceLevel + h * amplitude;
    }

    /** @return Which {@link Material} occupies the single point {@code p}. */
    public Material materialAt( VecF64 p ) {
        double surface = surfaceHeightAt(p.x(), p.z());
        if ( p.y() > surface )
            return p.y() <= seaLevel ? Material.WATER : Material.AIR;

        double depthBelow = surface - p.y();
        // Carve caves below the immediate surface using 3D noise.
        if ( depthBelow > grassDepth ) {
            double cave = noise.noise(p.x() * 0.05, p.y() * 0.05, p.z() * 0.05);
            if ( cave > caveThreshold )
                return p.y() <= seaLevel ? Material.WATER : Material.AIR;
        }
        if ( depthBelow <= grassDepth ) return Material.GRASS;
        if ( depthBelow <= soilDepth )  return Material.SOIL;
        return Material.ROCK;
    }

    /**
     *  Generates the sector covering {@code bounds}, subdividing adaptively up to
     *  {@code maxDepth} levels. The returned sector has its level-of-detail ether
     *  already {@link WorldSector#aggregated() aggregated} from the bottom up.
     */
    public WorldSector generate( BoundsF64 bounds, int maxDepth ) {
        return build(bounds, maxDepth).aggregated();
    }

    private WorldSector build( BoundsF64 bounds, int depth ) {
        Material homogeneous = homogeneousMaterial(bounds);
        if ( homogeneous != null )
            return WorldSector.leaf(bounds, WorldSectorEtherData.of(homogeneous));

        if ( depth <= 0 )
            return WorldSector.leaf(bounds, WorldSectorEtherData.of(dominantMaterial(bounds)));

        Tuple<BoundsF64> cells = bounds.subdivide(WorldTreeNode.RESOLUTION);
        WorldSector[] children = new WorldSector[WorldTreeNode.SECTOR_COUNT];
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
            children[i] = build(cells.get(i), depth - 1);
        return WorldSector.empty(bounds)
                           .withChildren(new WorldTreeNode(Tuple.of(WorldSector.class, children)));
    }

    /**
     *  @return The single material filling {@code bounds} if all sample points
     *          agree, otherwise {@code null} (meaning the region must be subdivided).
     */
    private Material homogeneousMaterial( BoundsF64 bounds ) {
        Material first = null;
        for ( VecF64 p : samplePoints(bounds) ) {
            Material m = materialAt(p);
            if ( first == null )
                first = m;
            else if ( !first.equals(m) )
                return null;
        }
        return first;
    }

    /**
     *  @return The most frequently sampled material across {@code bounds}. Used when
     *          detail bottoms out: a "block" is always a single material, so the
     *          dominant sample wins rather than recording any mixture.
     */
    private Material dominantMaterial( BoundsF64 bounds ) {
        Map<Material, Integer> counts = new HashMap<>();
        for ( VecF64 p : samplePoints(bounds) )
            counts.merge(materialAt(p), 1, Integer::sum);
        Material dominant = Material.AIR;
        int best = -1;
        for ( Map.Entry<Material, Integer> entry : counts.entrySet() ) {
            if ( entry.getValue() > best ) {
                best = entry.getValue();
                dominant = entry.getKey();
            }
        }
        return dominant;
    }

    /** The eight corners plus the center of {@code bounds}. */
    private static VecF64[] samplePoints( BoundsF64 bounds ) {
        VecF64 lo = bounds.min();
        VecF64 hi = bounds.max();
        return new VecF64[]{
                VecF64.of(lo.x(), lo.y(), lo.z()),
                VecF64.of(hi.x(), lo.y(), lo.z()),
                VecF64.of(lo.x(), hi.y(), lo.z()),
                VecF64.of(hi.x(), hi.y(), lo.z()),
                VecF64.of(lo.x(), lo.y(), hi.z()),
                VecF64.of(hi.x(), lo.y(), hi.z()),
                VecF64.of(lo.x(), hi.y(), hi.z()),
                VecF64.of(hi.x(), hi.y(), hi.z()),
                bounds.center()
        };
    }
}