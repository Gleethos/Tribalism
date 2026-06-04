package app.engine.world.render;

import app.engine.world.Texture;
import app.engine.world.TextureProfile;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/**
 *  Turns a {@link TextureProfile} into a representative {@link Color} for the
 *  first-draft Graphics2D renderer &mdash; a crude stand-in for the procedural
 *  noise shader these appearance qualities are ultimately meant to drive.
 *  <p>
 *  Each hue-bearing {@link Texture} quality is given a tint; a profile's colour is
 *  the intensity-weighted average of the tints of the qualities it carries. So a
 *  {@code grainy} surface reads tan, a {@code liquid} one blue, a {@code hairy}/
 *  {@code mossy} one green, and a "grainy liquid" lands somewhere between. Purely
 *  structural / optical qualities (roughness, reflectivity, …) contribute neutral
 *  tones; {@link Texture#OPACITY} is not a hue and instead governs <i>visibility</i>.
 *  <p>
 *  This lives in the rendering layer so the data model stays free of any AWT type.
 */
public final class TexturePalette
{
    /** A surface is drawn as a voxel only if it is at least this opaque on average. */
    public static final double VISIBILITY_THRESHOLD = 0.5;

    private static final Color DEFAULT = new Color(150, 150, 150);

    // A representative hue per quality. Structural/optical qualities use muted greys
    // so they tint towards stone/metal without overpowering the hue-bearing ones.
    private static final Map<Texture, Color> TINTS = new EnumMap<>(Texture.class);
    static {
        TINTS.put(Texture.GRAINY,      new Color(170, 140, 100)); // sand/soil tan
        TINTS.put(Texture.POWDERY,     new Color(235, 238, 245)); // snow white
        TINTS.put(Texture.LIQUID,      new Color( 60, 120, 210)); // water blue
        TINTS.put(Texture.WET,         new Color( 70, 110, 150)); // damp blue-grey
        TINTS.put(Texture.MOLTEN,      new Color(235,  95,  20)); // lava orange
        TINTS.put(Texture.EMISSIVE,    new Color(255, 170,  60)); // glow
        TINTS.put(Texture.FIBROUS,     new Color(120,  80,  45)); // wood brown
        TINTS.put(Texture.HAIRY,       new Color( 75, 150,  60)); // grass green
        TINTS.put(Texture.MOSSY,       new Color( 60, 110,  50)); // moss green
        TINTS.put(Texture.LEAFY,       new Color( 70, 140,  55)); // foliage green
        TINTS.put(Texture.CRYSTALLINE, new Color(150, 210, 225)); // gem cyan
        TINTS.put(Texture.METALLIC,    new Color(180, 182, 190)); // steel
        TINTS.put(Texture.REFLECTIVE,  new Color(200, 205, 215)); // bright grey
        TINTS.put(Texture.SHATTERED,   new Color(120, 120, 128)); // grey rubble
        TINTS.put(Texture.ROUGH,       new Color(125, 122, 120)); // stone grey
        TINTS.put(Texture.LAYERED,     new Color(140, 120, 100)); // sediment
        TINTS.put(Texture.VEINED,      new Color(150, 150, 160)); // marble grey
        TINTS.put(Texture.POROUS,      new Color(130, 120, 110)); // pitted grey-brown
        TINTS.put(Texture.SPIKY,       new Color(120, 120, 125)); // grey
    }

    private TexturePalette() {}

    /** @return {@code true} if a surface this opaque (on average) should be drawn. */
    public static boolean isVisible( double opacity ) {
        return opacity >= VISIBILITY_THRESHOLD;
    }

    /**
     *  @return The intensity-weighted blend of the tints of {@code profile}'s
     *          qualities, or a neutral grey if it carries no hue-bearing quality.
     */
    public static Color colorOf( TextureProfile profile ) {
        double r = 0, g = 0, b = 0, weight = 0;
        for ( Map.Entry<Texture, Color> entry : TINTS.entrySet() ) {
            double intensity = profile.intensityOf(entry.getKey());
            if ( intensity <= 0 )
                continue;
            Color tint = entry.getValue();
            r += tint.getRed()   * intensity;
            g += tint.getGreen() * intensity;
            b += tint.getBlue()  * intensity;
            weight += intensity;
        }
        if ( weight <= 0 )
            return DEFAULT;
        return new Color(clamp(r / weight), clamp(g / weight), clamp(b / weight));
    }

    private static int clamp( double v ) {
        int i = (int) Math.round(v);
        return i < 0 ? 0 : Math.min(i, 255);
    }
}