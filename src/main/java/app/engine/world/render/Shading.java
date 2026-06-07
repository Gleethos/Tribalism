package app.engine.world.render;

import app.engine.primitives.VecF64;

import java.awt.Color;

/**
 *  Flat directional shading with an ambient floor, shared by every renderer backend so a
 *  face is lit identically whether it is drawn by the software or the GPU path.
 */
public final class Shading
{
    private Shading() {}

    /**
     *  Shades a base colour by a face's orientation to the light.
     *
     *  @param base           The face's unlit colour.
     *  @param normal         The outward face normal.
     *  @param lightDirection The direction the light travels (normalized).
     *  @return The lit colour, clamped to valid component values.
     */
    public static Color shade( Color base, VecF64 normal, VecF64 lightDirection ) {
        double diffuse = Math.max(0, normal.dot(lightDirection.negate()));
        double brightness = 0.45 + 0.55 * diffuse;
        int r = clamp((int) Math.round(base.getRed()   * brightness));
        int g = clamp((int) Math.round(base.getGreen() * brightness));
        int b = clamp((int) Math.round(base.getBlue()  * brightness));
        return new Color(r, g, b);
    }

    private static int clamp( int v ) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}