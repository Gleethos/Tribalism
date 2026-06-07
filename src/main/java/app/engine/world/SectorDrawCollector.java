package app.engine.world;

/**
 *  The sink that {@link World#collectSectorsForRendering} hands every <i>render unit</i>
 *  it has decided is worth drawing for a frame, after frustum and occlusion culling.
 *  <p>
 *  A render unit is a sector (a sub-tree, a solid occluder, or a lone leaf) the renderer
 *  should turn into one mesh, together with the <b>level of detail</b> to mesh it at &mdash;
 *  the walk picks a coarser resolution for sectors that are smaller on screen, so distant
 *  terrain is meshed from big blocks of branch sectors rather than from leaves. This is the
 *  seam between the world (which knows <i>what</i> is visible, and at what detail) and a
 *  renderer (which knows <i>how</i> to draw it); a renderer implements this to mesh each
 *  surviving unit, and never traverses the world tree itself.
 */
@FunctionalInterface
public interface SectorDrawCollector
{
    /**
     *  Accepts one render unit the world has determined is (potentially) visible.
     *
     *  @param sector         The sector (sub-tree) to mesh and draw.
     *  @param view           The camera/projection context for this frame.
     *  @param meshResolution The grid resolution to mesh this unit at &mdash; a power of
     *                        {@value WorldTreeNode#RESOLUTION} ({@code 1, 8, 64}) chosen from the
     *                        unit's projected screen size; smaller is coarser. Passed on to
     *                        {@link app.engine.world.render.SectorMeshCache#meshOf(WorldSector, int)}
     *                        and used by retained-mesh backends as part of their cache key.
     */
    void collect( WorldSector sector, ViewInfo view, int meshResolution );
}