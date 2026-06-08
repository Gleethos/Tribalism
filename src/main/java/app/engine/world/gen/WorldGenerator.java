package app.engine.world.gen;

import app.engine.primitives.BoundsF64;
import app.engine.primitives.VecF64;
import app.engine.world.Material;
import app.engine.world.MaterialId;
import app.engine.world.Side;
import app.engine.world.TextureProfile;
import app.engine.world.WorldSector;
import app.engine.world.WorldSectorEtherData;
import app.engine.world.WorldTreeNode;
import sprouts.Tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
                /* amplitude          */  94,
                /* frequency          */  0.015,
                /* soilDepth          */  6,
                /* grassDepth         */  1.5,
                /* caveThreshold      */  0.65,
                /* generationDistance */  96,
                /* detailDepth        */  2,
                /* chunkSize          */  64
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
        WorldSector built = build(bounds, maxDepth).aggregated();
        if ( homogeneousMaterial(bounds) != null )
            return built; // uniform: inset 0 already, matching etherOf's homogeneous short-circuit.
        // Attach the SAME top-down per-side insets etherOf uses, so a coarse node and its one-level
        // refinement agree on shape as well as appearance (keeping etherOf(b) == generate(b, 1).ether()),
        // and a distant generated chunk drawn as a single box still shrinks to its surface.
        return built.withEther(withInsets(built.ether(), insetsBySide(bounds)));
    }

    /**
     *  Describes a whole region's appearance <b>top-down</b>, directly from the noise &mdash; the
     *  level-of-detail summary of {@code bounds} <i>without building (or even owning) its sub-tree</i>.
     *  <p>
     *  This is the inverse of {@link WorldSector#aggregated() bottom-up aggregation}: instead of
     *  averaging the ether of children that must first exist, it samples the region's own
     *  {@value WorldTreeNode#RESOLUTION}-cubed grid of cells analytically &mdash; each cell's
     *  {@link #dominantMaterial dominant material}, the whole-cube material their
     *  {@link MaterialId#merge merge}, and each face the {@link TextureProfile#average average} of the
     *  {@link WorldTreeNode#boundaryCells boundary cells} on that face. (A uniform region short-circuits
     *  to its single exact material, exactly as {@link #build} collapses one into a leaf.) It is what
     *  lets a coarse sector exist and be drawn while its detail is unloaded (or evicted), which is the
     *  basis for rendering terrain kilometres into the distance at bounded cost.
     *  <p>
     *  <b>Faithfulness.</b> By construction this equals the bottom-up ether of a one-level build of
     *  the same region: {@code etherOf(b)} is identical to {@code generate(b, 1).ether()} (both reduce
     *  to the same per-cell dominant materials and the same per-face boundary average). So a coarse
     *  node and its one-level refinement agree, which is what keeps level-of-detail transitions from
     *  popping. Deeper refinements simply describe each child region with its own {@code etherOf}.
     *
     *  @param bounds The region to summarize.
     *  @return The region's representative material and per-side appearance, derived purely from noise.
     */
    /**
     *  @return A cheap, <i>uniform</i> top-down appearance for a whole region: its single
     *          {@link #dominantMaterial dominant sampled material} on every face, carrying that region's
     *          per-side {@link TextureProfile#inset() insets} (so a coarse box drawn from it still shrinks
     *          to fit the surface). This is the coarse counterpart to {@link #etherOf}: it uses one
     *          dominant material rather than a faithful per-side appearance, so it is cheap enough for the
     *          <i>many</i> coarse level-of-detail leaves a far camera materializes. A uniform region
     *          short-circuits (no inset sampling); only a surface-straddling region pays for the
     *          {@value WorldTreeNode#RESOLUTION}&sup3; inset grid.
     */
    public WorldSectorEtherData representativeEtherOf( BoundsF64 bounds ) {
        Material homogeneous = homogeneousMaterial(bounds);
        if ( homogeneous != null )
            return WorldSectorEtherData.of(homogeneous); // uniform: solid fills it, or pure air (never drawn) - no recession.
        // Straddling the surface: represent the GROUND (the dominant VISIBLE material - not the volumetric
        // majority, which is often the empty air above and would make the box vanish), recessed on each side
        // down to where the air begins. So a half-buried region draws its ground shrunk to fit, never a cube
        // and never nothing.
        WorldSectorEtherData ether = WorldSectorEtherData.of(dominantVisibleMaterial(bounds));
        return withInsets(ether, insetsBySide(bounds));
    }

    /**
     *  @return {@code true} if {@code bounds} samples as a single uniform material (it does not straddle
     *          a surface or material boundary). Such a region is identical at every level of detail, so
     *          there is nothing to gain by refining it &mdash; the level-of-detail walk subdivides only
     *          <i>non</i>-homogeneous regions, which keeps refinement bounded to the (2D) terrain surface
     *          rather than the (3D) solid/empty volume around it.
     */
    public boolean isHomogeneous( BoundsF64 bounds ) {
        return homogeneousMaterial(bounds) != null;
    }

    public WorldSectorEtherData etherOf( BoundsF64 bounds ) {
        Material homogeneous = homogeneousMaterial(bounds);
        if ( homogeneous != null )
            return WorldSectorEtherData.of(homogeneous); // uniform region: one exact material, inset 0, no averaging drift.

        Tuple<BoundsF64> cells = bounds.subdivide(WorldTreeNode.RESOLUTION);
        Material[] cellMaterials = new Material[WorldTreeNode.SECTOR_COUNT];
        List<MaterialId> ids = new ArrayList<>(WorldTreeNode.SECTOR_COUNT);
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ ) {
            Material material = dominantMaterial(cells.get(i));
            cellMaterials[i] = material;
            ids.add(material.materialId());
        }

        // Each face's APPEARANCE is the average of the boundary cells on that face; its SHAPE (how far the
        // face recedes to meet the content) is the analytic inset from insetsBySide - the two top-down
        // per-side descriptions of the region.
        WorldSectorEtherData ether = WorldSectorEtherData.empty().withMaterial(MaterialId.merge(ids));
        for ( Side side : Side.values() ) {
            int[] boundary = WorldTreeNode.boundaryCells(side);
            List<TextureProfile> faces = new ArrayList<>(boundary.length);
            for ( int cell : boundary )
                faces.add(cellMaterials[cell].texture());
            ether = ether.withSide(side, TextureProfile.average(faces));
        }
        return withInsets(ether, insetsBySide(bounds));
    }

    /**
     *  @return The per-side insets of {@code bounds} (indexed by {@link Side#ordinal()}), read straight from
     *          the surface height field. An inset is the fraction of the box, measured inward from a face,
     *          that is empty (air) before the content begins, so the renderer can shrink the box to fit the
     *          terrain. Every face is considered, not just the top:
     *          <ul>
     *            <li><b>+Y (top)</b> recedes down to the <i>highest</i> terrain over the footprint, so the
     *                box top sits on the surface and never clips it.</li>
     *            <li><b>a horizontal face</b> (&plusmn;X, &plusmn;Z) recedes inward as long as the terrain
     *                there stays below the box floor &mdash; i.e. it fronts only air. This shapes cliffs and
     *                steep slopes, where the ground enters the box from a side rather than from the top.</li>
     *            <li><b>-Y (bottom)</b> never recedes: terrain is solid all the way down.</li>
     *          </ul>
     *          The content surface is {@code max(surfaceHeightAt, seaLevel)} (air sits above sea level, water
     *          below). Continuous (the top is exact; a side interpolates the slice where the terrain crosses
     *          the floor), so neighbouring boxes shrink to their own local terrain and form a terrace; and
     *          cheap &mdash; an {@value WorldTreeNode#RESOLUTION}&sup2; footprint of {@code surfaceHeightAt}
     *          samples, not a {@value WorldTreeNode#RESOLUTION}&sup3; material grid.
     */
    private double[] insetsBySide( BoundsF64 bounds ) {
        double[] insets = new double[Side.values().length];
        VecF64 lo = bounds.min(), hi = bounds.max();
        double width = hi.x() - lo.x(), height = hi.y() - lo.y(), depth = hi.z() - lo.z();
        if ( width <= 0 || height <= 0 || depth <= 0 )
            return insets;

        int n = WorldTreeNode.RESOLUTION;
        double maxTop = Double.NEGATIVE_INFINITY;        // highest terrain anywhere over the footprint
        double[] colMaxX = new double[n];                // per x-slice: highest terrain across z
        double[] colMaxZ = new double[n];                // per z-slice: highest terrain across x
        java.util.Arrays.fill(colMaxX, Double.NEGATIVE_INFINITY);
        java.util.Arrays.fill(colMaxZ, Double.NEGATIVE_INFINITY);
        for ( int ix = 0; ix < n; ix++ ) {
            double x = lo.x() + (ix + 0.5) * width / n;
            for ( int iz = 0; iz < n; iz++ ) {
                double z = lo.z() + (iz + 0.5) * depth / n;
                double contentTop = Math.max(surfaceHeightAt(x, z), seaLevel);
                maxTop = Math.max(maxTop, contentTop);
                colMaxX[ix] = Math.max(colMaxX[ix], contentTop);
                colMaxZ[iz] = Math.max(colMaxZ[iz], contentTop);
            }
        }

        insets[Side.POS_Y.ordinal()] = clampUnit((hi.y() - maxTop) / height);
        insets[Side.NEG_X.ordinal()] = emptyFraction(colMaxX, lo.y(), false);
        insets[Side.POS_X.ordinal()] = emptyFraction(colMaxX, lo.y(), true);
        insets[Side.NEG_Z.ordinal()] = emptyFraction(colMaxZ, lo.y(), false);
        insets[Side.POS_Z.ordinal()] = emptyFraction(colMaxZ, lo.y(), true);
        return insets;
    }

    private static double clampUnit( double v ) {
        return Math.max(0, Math.min(1, v));
    }

    /**
     *  Walks the slices of {@code colMax} (the highest terrain per slice along one horizontal axis) inward
     *  from one end, summing the slices whose terrain stays below {@code floor} (so the box face there fronts
     *  only air), plus a fractional slice where the terrain rises through the floor.
     *
     *  @param colMax   Highest terrain per slice along the axis.
     *  @param floor    The box's bottom y; terrain below this is empty air against this side.
     *  @param fromHigh Walk inward from the high-index (positive) end rather than the low (negative) end.
     *  @return The empty fraction in {@code [0, 1]} of the axis adjacent to that face.
     */
    private static double emptyFraction( double[] colMax, double floor, boolean fromHigh ) {
        int n = colMax.length;
        double cells = 0;
        double previous = Double.NaN;
        for ( int k = 0; k < n; k++ ) {
            double current = colMax[fromHigh ? n - 1 - k : k];
            if ( current < floor ) {       // this whole slice fronts air
                cells += 1;
                previous = current;
                continue;
            }
            if ( !Double.isNaN(previous) && current > previous )
                cells += (floor - previous) / (current - previous); // terrain crosses the floor within this slice
            break;
        }
        return Math.min(1.0, cells / n);
    }

    /** @return {@code ether} with each side's appearance recessed by the matching entry of {@code insets}. */
    private static WorldSectorEtherData withInsets( WorldSectorEtherData ether, double[] insets ) {
        for ( Side side : Side.values() ) {
            double inset = insets[side.ordinal()];
            if ( inset > 0 )
                ether = ether.withSide(side, ether.sideOf(side).withInset(inset));
        }
        return ether;
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
                           .withChildren(new WorldTreeNode(children));
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

    /**
     *  @return The most frequently sampled <i>visible</i> (non-air) material across {@code bounds} &mdash; the
     *          ground a surface-straddling region represents. Falls back to {@link #dominantMaterial} only if
     *          no sample is visible (which {@link #homogeneousMaterial} already routes to the air short-circuit).
     */
    private Material dominantVisibleMaterial( BoundsF64 bounds ) {
        Map<Material, Integer> counts = new HashMap<>();
        for ( VecF64 p : samplePoints(bounds) ) {
            Material m = materialAt(p);
            if ( !m.texture().isInvisible() )
                counts.merge(m, 1, Integer::sum);
        }
        Material dominant = null;
        int best = 0;
        for ( Map.Entry<Material, Integer> entry : counts.entrySet() ) {
            if ( entry.getValue() > best ) {
                best = entry.getValue();
                dominant = entry.getKey();
            }
        }
        return dominant != null ? dominant : dominantMaterial(bounds);
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