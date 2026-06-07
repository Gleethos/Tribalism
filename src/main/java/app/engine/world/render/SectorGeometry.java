package app.engine.world.render;

import app.engine.primitives.BoundsF64;
import app.engine.world.Side;
import app.engine.world.TextureProfile;
import app.engine.world.World;
import app.engine.world.WorldSector;
import app.engine.world.WorldSectorEtherData;

import java.util.function.Consumer;

/**
 *  Turns a sector the world has decided is visible into the {@link Quad}s that depict it,
 *  shared by every renderer backend so they show identical geometry. It mirrors the
 *  level-of-detail decision handed down by {@link World#collectSectorsForRendering}:
 *  <ul>
 *      <li>a fully-solid, too-small or lone sector becomes one coarse, inset-fitted box;</li>
 *      <li>a full-detail block of leaf voxels becomes its cached, greedy-meshed surface.</li>
 *  </ul>
 *  Each backend supplies a {@code sink} that consumes the quads (the software path
 *  projects and fills them; the GPU path appends them to a vertex buffer).
 */
public final class SectorGeometry
{
    private SectorGeometry() {}

    /**
     *  Emits the quads depicting one visible sector to {@code sink}.
     *
     *  @param sector      The sector to depict.
     *  @param wantsDetail Whether the world judged it large enough on screen for detail.
     *  @param meshCache   The cache providing (and memoizing) greedy meshes of detail blocks.
     *  @param sink        Receives each quad to draw.
     */
    public static void emit( WorldSector sector, boolean wantsDetail, SectorMeshCache meshCache, Consumer<Quad> sink ) {
        if ( isMeshBlock(sector, wantsDetail) ) {
            for ( Quad quad : meshCache.meshOf(sector).quads() )
                sink.accept(quad);
        } else if ( sector.isSolidOpaque()
                 || ((!wantsDetail || sector.isLeaf()) && sector.ether().isMajorityOpaque()) ) {
            emitBox(sector, sink);
        }
    }

    /**
     *  Decides whether a visible sector is drawn as a detailed mesh rather than a box.
     *
     *  @return {@code true} if this visible sector should be drawn as a full-detail
     *          greedy {@link SectorMeshCache mesh} (a block of leaf voxels worth its
     *          detail), as opposed to a single coarse box. This is the one case whose
     *          geometry is heavy and worth retaining on the GPU; everything else is a
     *          cheap box. (Kept as a predicate so a retained-geometry backend can route
     *          mesh blocks to a per-block cache while still using {@link #emit} for boxes.)
     */
    public static boolean isMeshBlock( WorldSector sector, boolean wantsDetail ) {
        return !sector.isSolidOpaque() && wantsDetail && !sector.isLeaf() && sector.hasOnlyLeafChildren();
    }

    private static void emitBox( WorldSector sector, Consumer<Quad> sink ) {
        BoundsF64 bounds = sector.insets().shrink(sector.bounds());
        WorldSectorEtherData ether = sector.ether();
        for ( Side side : Side.values() ) {
            TextureProfile profile = WorldRenderer.faceProfile(ether, side);
            if ( !profile.isInvisible() )
                sink.accept(Cubes.faceQuad(bounds, side, profile));
        }
    }
}