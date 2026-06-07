package app.engine.world;

/**
 *  The sink that {@link World#collectSectorsForRendering} hands every <i>render unit</i>
 *  it has decided is worth drawing for a frame, after frustum and occlusion culling.
 *  <p>
 *  A render unit is a chunk-sized sector (or a solid occluder, or a lone leaf): a whole
 *  sub-tree the renderer should turn into one mesh. This is the seam between the world
 *  (which knows <i>what</i> is visible) and a renderer (which knows <i>how</i> to draw
 *  it); a renderer implements this to mesh each surviving unit, and never traverses the
 *  world tree itself.
 */
@FunctionalInterface
public interface SectorDrawCollector
{
    /**
     *  Accepts one render unit the world has determined is (potentially) visible.
     *
     *  @param sector The chunk-sized sector (sub-tree) to mesh and draw.
     *  @param view   The camera/projection context for this frame.
     */
    void collect( WorldSector sector, ViewInfo view );
}