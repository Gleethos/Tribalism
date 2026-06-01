package app.engine.world;

import app.engine.primitives.BoundsF64;
import app.engine.primitives.VecF64;

/**
 *  A light in the world, modelled as a simple shape together with basic light
 *  properties (intensity and colour).
 *  <p>
 *  Like a {@link WorldTreeEntityId}, a light has a unique {@code id} and a
 *  spatial {@code bounds}, so the engine can place it in the world tree and
 *  look it up positionally. This is a sum type (a sealed interface implemented
 *  only by records), so callers can exhaustively switch over the concrete shapes.
 *  Colours are expressed as {@link VecF64} RGB triples.
 */
public sealed interface LightSource
        permits LightSource.Sphere, LightSource.Cube, LightSource.Plane
{
    /** @return The unique identifier of this light. */
    long id();

    /** @return The light's emissive intensity (non-negative). */
    double intensity();

    /** @return The light's colour as an RGB triple. */
    VecF64 color();

    /** @return The center of the light in world space. */
    VecF64 position();

    /** @return The axis-aligned bounds the light occupies, used for tree placement. */
    BoundsF64 bounds();

    /**
     *  A spherical (point/omnidirectional) light, radiating equally in all
     *  directions from {@code center} out to {@code radius}.
     */
    record Sphere(
        long id,
        VecF64 center,
        double radius,
        double intensity,
        VecF64 color
    ) implements LightSource {
        public Sphere {
            if ( radius < 0 )    throw new IllegalArgumentException("The radius must not be negative, but was " + radius + ".");
            if ( intensity < 0 ) throw new IllegalArgumentException("The intensity must not be negative, but was " + intensity + ".");
        }
        @Override public VecF64 position() { return center; }
        @Override public BoundsF64 bounds() { return BoundsF64.cube(center, radius * 2); }
    }

    /** A box-shaped light filling the given {@code bounds}. */
    record Cube(
        long id,
        BoundsF64 bounds,
        double intensity,
        VecF64 color
    ) implements LightSource {
        public Cube {
            if ( intensity < 0 ) throw new IllegalArgumentException("The intensity must not be negative, but was " + intensity + ".");
        }
        @Override public VecF64 position() { return bounds.center(); }
    }

    /**
     *  A planar (area/directional) light at {@code center}, facing along
     *  {@code normal}, emitting from a square patch of the given {@code extent}.
     */
    record Plane(
        long id,
        VecF64 center,
        VecF64 normal,
        double extent,
        double intensity,
        VecF64 color
    ) implements LightSource {
        public Plane {
            if ( extent < 0 )    throw new IllegalArgumentException("The extent must not be negative, but was " + extent + ".");
            if ( intensity < 0 ) throw new IllegalArgumentException("The intensity must not be negative, but was " + intensity + ".");
        }
        @Override public VecF64 position() { return center; }
        @Override public BoundsF64 bounds() { return BoundsF64.cube(center, extent * 2); }
    }
}