package app.engine.world.render;

import app.engine.world.Material;

import java.awt.Color;

/**
 *  Maps a {@link Material} to a representative {@link Color} for the first-draft
 *  Graphics2D renderer. This deliberately lives in the rendering layer so the
 *  data model ({@code app.engine.world}) stays free of any AWT dependency.
 */
public final class MaterialPalette
{
    private MaterialPalette() {}

    public static Color colorOf( Material material ) {
        return switch ( material ) {
            case AIR     -> new Color(0, 0, 0, 0);
            case WATER   -> new Color(64, 128, 220);
            case SOIL    -> new Color(120, 85, 55);
            case GRASS   -> new Color(70, 150, 60);
            case SAND    -> new Color(210, 200, 140);
            case ROCK    -> new Color(130, 130, 135);
            case WOOD    -> new Color(110, 75, 40);
            case METAL   -> new Color(180, 180, 190);
            case ORGANIC -> new Color(90, 140, 70);
        };
    }

    /** @return {@code true} if this material is invisible and should never be drawn. */
    public static boolean isTransparent( Material material ) {
        return material == Material.AIR;
    }
}