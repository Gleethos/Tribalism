package app.engine.world;

/**
 *  The sink that {@link World#collectSectorsForRendering} hands every sector it has
 *  decided is worth drawing for a frame, after frustum culling, occlusion culling and
 *  the level-of-detail decision have all been applied.
 *  <p>
 *  This is the seam between the world (which knows <i>what</i> is visible) and a
 *  renderer (which knows <i>how</i> to draw it). A renderer implements this to turn
 *  each surviving sector into pixels; it never has to traverse the world tree itself.
 */
@FunctionalInterface
public interface SectorDrawCollector
{
    /**
     *  Accepts one sector the world has determined is (potentially) visible.
     *
     *  @param sector      The sector to draw.
     *  @param wantsDetail {@code true} if the sector is large enough on screen to be
     *                     worth drawing in detail; {@code false} if it should be drawn
     *                     as a single coarse, level-of-detail box. (For a fully-solid
     *                     occluder this is {@code false}: it is always one box.)
     *  @param view        The camera/projection context for this frame.
     */
    void collect( WorldSector sector, boolean wantsDetail, ViewInfo view );
}