package app.engine.world.render;

/**
 *  Per-frame rendering statistics for one screen, reported by a {@link Renderer} for
 *  diagnostics, an on-screen HUD, and A/B comparison between backends.
 *
 *  @param facesDrawn              The number of faces/quads actually drawn this frame.
 *  @param occlusionCulledSectors  The number of sectors (and their sub-trees) skipped by
 *                                 occlusion culling this frame.
 */
public record FrameStats(int facesDrawn, int occlusionCulledSectors)
{
    /** The stats of a screen that has not been drawn yet. */
    public static final FrameStats NONE = new FrameStats(0, 0);
}