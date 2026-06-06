package app.engine.world.render;

import app.engine.world.ScreenId;
import app.engine.world.World;

import java.awt.Component;

/**
 *  A swappable rendering <b>backend</b>: it turns an immutable {@link World} into pixels
 *  for one or more {@link app.engine.world.Screen Screen}s, and hands the host
 *  application a Swing component to display each one.
 *  <p>
 *  This is the coarse seam that keeps the low-level graphics path swappable. The world
 *  model already knows nothing of rendering ({@link World#collectSectorsForRendering}
 *  exposes <i>what</i> is visible without any rendering type); this interface hides
 *  everything about <i>how</i> it becomes pixels &mdash; the graphics API
 *  (OpenGL/Vulkan/&hellip;), the buffer or surface, the context and its threading, the
 *  shaders, and even whether rendering happens on the GPU at all. A backend that needs
 *  shaders keeps them entirely inside itself; nothing API-specific crosses this
 *  boundary. The interface therefore speaks in {@code World}/{@code ScreenId} terms, not
 *  triangles or buffers.
 *  <p>
 *  Deliberately <b>coarse</b>: a 3D rendering path is retained and batched (geometry is
 *  uploaded once, not re-issued per frame), and OpenGL and Vulkan disagree too violently
 *  on pipelines, descriptor sets and synchronization to hide behind fine-grained drawing
 *  primitives. So the swappable unit is the whole renderer, not a {@code Graphics2D}-style
 *  per-call API.
 *  <p>
 *  Backends so far: {@link Graphics2DRenderer} (software, {@link java.awt.Graphics2D}).
 *  An OpenGL backend will live in {@code render.gl} behind this same interface. The
 *  interface is <i>extracted</i> from concrete backends and is expected to evolve as the
 *  second one lands &mdash; it is not designed ahead of them.
 */
public interface Renderer extends AutoCloseable
{
    /**
     *  The AWT/Swing component that displays {@code screenId}'s view. Each backend
     *  integrates with the UI differently &mdash; the software backend gives a lightweight
     *  {@link javax.swing.JPanel}, the GL backend a heavyweight {@code AWTGLCanvas} &mdash;
     *  so the return type is their common supertype {@link Component}, and producing the
     *  viewport is part of the backend. Created on first request for an id and reused
     *  thereafter.
     *
     *  @param screenId The screen whose viewport component is wanted.
     *  @return The component to add to the UI; the same instance for the same id.
     */
    Component viewportFor( ScreenId screenId );

    /**
     *  Publishes the latest immutable world to draw. A {@code World} is a deeply immutable
     *  value, so this is safe to call from the simulation thread while the backend renders
     *  the most recently published one. The most recent call wins.
     *
     *  @param world The world the next frame(s) should depict.
     */
    void setWorld( World world );

    /**
     *  Reports drawing statistics for a screen's most recent frame.
     *
     *  @param screenId The screen to report on.
     *  @return Statistics for the most recent frame drawn for {@code screenId}, or
     *          {@link FrameStats#NONE} if nothing has been drawn yet. For diagnostics and
     *          A/B comparison between backends.
     */
    FrameStats stats( ScreenId screenId );

    /** Stops any render loop and releases backend resources (native buffers, threads, contexts). */
    @Override
    void close();
}