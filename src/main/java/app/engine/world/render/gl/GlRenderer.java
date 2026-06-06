package app.engine.world.render.gl;

import app.engine.world.ScreenId;
import app.engine.world.World;
import app.engine.world.render.FrameStats;
import app.engine.world.render.Renderer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;

import java.awt.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_LESS;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDepthFunc;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glViewport;

/**
 *  The OpenGL rendering backend (behind the {@link Renderer} SPI), drawing directly into
 *  a heavyweight {@code AWTGLCanvas} &mdash; no read-back, a real depth buffer.
 *  <p>
 *  <b>Milestone 1 (this version):</b> bring up the context and clear each viewport to the
 *  sky colour, on a dedicated render thread. This validates the hard part &mdash; a GL
 *  context inside an AWT canvas, off the EDT, with the native libraries &mdash; before any
 *  geometry exists. The world is already accepted via {@link #setWorld} so the next
 *  milestone (diff-driven per-chunk VBOs, depth-tested, front-to-back) only has to fill
 *  {@code Viewport.paintGL}.
 *  <p>
 *  <b>Threading:</b> a single {@code gl-renderer} thread owns rendering and calls
 *  {@link AWTGLCanvas#render()} on each viewport in turn; each call makes that viewport's
 *  (shared) context current. The EDT is never blocked by a frame. A {@code World} is a
 *  deeply immutable value, so {@link #setWorld} hands it across threads without locking.
 *  <p>
 *  <b>Shared context:</b> the first canvas created is the share root; every later canvas
 *  shares its GL context (via {@link GLData#shareContext}), so chunk VBOs and the texture
 *  atlas upload once and are reused across all screens.
 */
public final class GlRenderer implements Renderer
{
    private static final float SKY_R = 135f / 255f;
    private static final float SKY_G = 180f / 255f;
    private static final float SKY_B = 235f / 255f;
    private static final long FRAME_MILLIS = 8L; // render-thread pacing (~120 Hz cap)

    private final AtomicReference<@Nullable World> _world = new AtomicReference<>();
    private final Map<ScreenId, Viewport> _viewports = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Viewport> _canvases = new CopyOnWriteArrayList<>();
    private final Map<ScreenId, FrameStats> _stats = new ConcurrentHashMap<>();
    private final Thread _renderThread;
    private volatile boolean _running = true;
    private @Nullable AWTGLCanvas _shareRoot; // first canvas; the others share its context

    public GlRenderer() {
        _renderThread = new Thread(this::renderLoop, "gl-renderer");
        _renderThread.setDaemon(true);
        _renderThread.start();
    }

    @Override
    public synchronized Component viewportFor( ScreenId screenId ) {
        return _viewports.computeIfAbsent(screenId, id -> {
            GLData data = new GLData();
            data.majorVersion = 3;
            data.minorVersion = 3;
            data.profile = GLData.Profile.CORE;
            data.depthSize = 24;
            data.doubleBuffer = true;
            data.shareContext = _shareRoot; // null for the first canvas
            Viewport viewport = new Viewport(id, data);
            if ( _shareRoot == null )
                _shareRoot = viewport;
            _canvases.add(viewport);
            return viewport;
        });
    }

    @Override
    public void setWorld( World world ) {
        _world.set(world);
    }

    @Override
    public FrameStats stats( ScreenId screenId ) {
        return _stats.getOrDefault(screenId, FrameStats.NONE);
    }

    @Override
    public void close() {
        _running = false;
        _renderThread.interrupt();
        try {
            _renderThread.join(500);
        } catch ( InterruptedException e ) {
            Thread.currentThread().interrupt();
        }
    }

    private void renderLoop() {
        while ( _running ) {
            for ( Viewport viewport : _canvases ) {
                if ( !viewport.isValid() )
                    continue; // not yet realized on screen; render() would fail
                try {
                    viewport.render();
                } catch ( RuntimeException ignored ) {
                    // The canvas can momentarily be un-renderable (resize/reparent); try next frame.
                }
            }
            try {
                Thread.sleep(FRAME_MILLIS);
            } catch ( InterruptedException e ) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     *  One heavyweight GL canvas displaying a single screen. {@link #initGL}/{@link #paintGL}
     *  run on the {@code gl-renderer} thread, where the canvas's context is current.
     */
    private static final class Viewport extends AWTGLCanvas
    {
        @SuppressWarnings("unused") // used by the next milestone to resolve the camera per screen
        private final ScreenId _screenId;

        Viewport( ScreenId screenId, GLData data ) {
            super(data);
            _screenId = screenId;
        }

        @Override
        public void initGL() {
            createCapabilities();
            glClearColor(SKY_R, SKY_G, SKY_B, 1f);
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LESS);
        }

        @Override
        public void paintGL() {
            glViewport(0, 0, getFramebufferWidth(), getFramebufferHeight());
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            // Milestone 1: just the sky. Next: diff(World) -> per-chunk VBOs, drawn front-to-back.
            swapBuffers();
        }
    }
}