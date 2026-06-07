package app.engine.world.render.gl;

import app.engine.primitives.Mat4F64;
import app.engine.primitives.VecF64;
import app.engine.world.ScreenId;
import app.engine.world.World;
import app.engine.world.render.FrameStats;
import app.engine.world.render.Quad;
import app.engine.world.render.Renderer;
import app.engine.world.render.SectorGeometry;
import app.engine.world.render.SectorMeshCache;
import app.engine.world.render.Shading;
import app.engine.world.render.TexturePalette;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;
import org.lwjgl.system.MemoryUtil;

import java.awt.Color;
import java.awt.Component;
import java.nio.FloatBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.opengl.GL.createCapabilities;

/**
 *  The OpenGL rendering backend (behind the {@link Renderer} SPI), drawing the world
 *  directly into a heavyweight {@code AWTGLCanvas} &mdash; no read-back, a real depth
 *  buffer (so no painter's sort and no overdraw cost beyond the depth test).
 *  <p>
 *  <b>This milestone (2):</b> real geometry. Each frame the same visible sectors the
 *  software path uses ({@link World#collectSectorsForRendering} &rarr; {@link SectorGeometry})
 *  are turned into triangles, with each face's flat {@link Shading shade} baked into the
 *  vertex colour, uploaded to a per-viewport vertex buffer and drawn depth-tested. The
 *  picture should match {@code Graphics2DRenderer} but without the sort or the overdraw.
 *  <p>
 *  It re-uploads the collected geometry each frame for now; the next step is the
 *  diff-driven <i>retained</i> per-chunk VBOs (upload once, free on removal) so static
 *  terrain is not resent every frame.
 *  <p>
 *  <b>Threading:</b> a single {@code gl-renderer} thread owns rendering and calls
 *  {@link AWTGLCanvas#render()} on each viewport in turn (each call makes that viewport's
 *  shared context current and runs {@code initGL}/{@code paintGL}). The EDT is never
 *  blocked. A {@code World} is a deeply immutable value, so {@link #setWorld} hands it
 *  across threads without locking. The shared mesh cache is touched only on this thread.
 */
public final class GlRenderer implements Renderer
{
    private static final float SKY_R = 135f / 255f;
    private static final float SKY_G = 180f / 255f;
    private static final float SKY_B = 235f / 255f;
    private static final long FRAME_MILLIS = 8L; // render-thread pacing (~120 Hz cap)
    private static final double REFINE_THRESHOLD_PX = 28.0;
    private static final VecF64 LIGHT = VecF64.of(-0.4, -1.0, -0.3).normalize();

    private static final String VERTEX_SHADER =
            "#version 330 core\n" +
            "layout(location=0) in vec3 aPos;\n" +
            "layout(location=1) in vec3 aColor;\n" +
            "uniform mat4 uMvp;\n" +
            "out vec3 vColor;\n" +
            "void main() {\n" +
            "    gl_Position = uMvp * vec4(aPos, 1.0);\n" +
            "    vColor = aColor;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 330 core\n" +
            "in vec3 vColor;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = vec4(vColor, 1.0);\n" +
            "}\n";

    private final AtomicReference<@Nullable World> _world = new AtomicReference<>();
    private final Map<ScreenId, Viewport> _viewports = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Viewport> _canvases = new CopyOnWriteArrayList<>();
    private final Map<ScreenId, FrameStats> _stats = new ConcurrentHashMap<>();
    private final SectorMeshCache _meshCache = new SectorMeshCache(); // gl-renderer thread only
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

    private static final int FLOATS_PER_VERTEX = 6;   // x,y,z, r,g,b
    private static final int VERTICES_PER_QUAD = 6;   // two triangles
    private static final int FLOATS_PER_QUAD = FLOATS_PER_VERTEX * VERTICES_PER_QUAD;

    /**
     *  One heavyweight GL canvas displaying a single screen. {@link #initGL}/{@link #paintGL}
     *  run on the {@code gl-renderer} thread, where the canvas's shared context is current.
     */
    private final class Viewport extends AWTGLCanvas
    {
        private final ScreenId _screenId;

        private int _program;
        private int _vao;
        private int _vbo;
        private int _mvpLocation;
        private FloatBuffer _vertices = MemoryUtil.memAllocFloat(FLOATS_PER_QUAD * 4096);
        private final float[] _mvp = new float[16];
        private boolean _mvpReady;
        private int _quadCount;

        Viewport( ScreenId screenId, GLData data ) {
            super(data);
            _screenId = screenId;
        }

        @Override
        public void initGL() {
            createCapabilities();
            GL11.glClearColor(SKY_R, SKY_G, SKY_B, 1f);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LESS);

            _program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            _mvpLocation = GL20.glGetUniformLocation(_program, "uMvp");

            _vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(_vao);
            _vbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, _vbo);
            int stride = FLOATS_PER_VERTEX * Float.BYTES;
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 3L * Float.BYTES);
            GL20.glEnableVertexAttribArray(1);
            GL30.glBindVertexArray(0);
        }

        @Override
        public void paintGL() {
            GL11.glViewport(0, 0, getFramebufferWidth(), getFramebufferHeight());
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            World world = _world.get();
            int occlusionCulled = 0;
            if ( world != null ) {
                _vertices.clear();
                _quadCount = 0;
                _mvpReady = false;
                World.RenderStats stats = world.collectSectorsForRendering(
                        _screenId, REFINE_THRESHOLD_PX,
                        ( sector, wantsDetail, view ) -> {
                            if ( !_mvpReady ) {
                                captureMvp(view.viewProjection());
                                _mvpReady = true;
                            }
                            SectorGeometry.emit(sector, wantsDetail, _meshCache, this::appendQuad);
                        });
                occlusionCulled = stats.occlusionCulledSectors();

                if ( _mvpReady && _quadCount > 0 ) {
                    GL20.glUseProgram(_program);
                    GL20.glUniformMatrix4fv(_mvpLocation, false, _mvp);
                    GL30.glBindVertexArray(_vao);
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, _vbo);
                    _vertices.flip();
                    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, _vertices, GL15.GL_DYNAMIC_DRAW);
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, _quadCount * VERTICES_PER_QUAD);
                    GL30.glBindVertexArray(0);
                }
            }
            _stats.put(_screenId, new FrameStats(_quadCount, occlusionCulled));
            swapBuffers();
        }

        /** Appends a quad as two triangles (c0,c1,c2 / c0,c2,c3), its flat shade baked into the colour. */
        private void appendQuad( Quad quad ) {
            ensureCapacity(FLOATS_PER_QUAD);
            Color color = Shading.shade(TexturePalette.colorOf(quad.profile()), quad.normal(), LIGHT);
            float r = color.getRed()   / 255f;
            float g = color.getGreen() / 255f;
            float b = color.getBlue()  / 255f;
            appendVertex(quad.c0(), r, g, b);
            appendVertex(quad.c1(), r, g, b);
            appendVertex(quad.c2(), r, g, b);
            appendVertex(quad.c0(), r, g, b);
            appendVertex(quad.c2(), r, g, b);
            appendVertex(quad.c3(), r, g, b);
            _quadCount++;
        }

        private void appendVertex( VecF64 p, float r, float g, float b ) {
            _vertices.put((float) p.x()).put((float) p.y()).put((float) p.z()).put(r).put(g).put(b);
        }

        private void ensureCapacity( int extraFloats ) {
            if ( _vertices.remaining() < extraFloats ) {
                int newCapacity = Math.max(_vertices.capacity() * 2, _vertices.capacity() + extraFloats);
                _vertices = MemoryUtil.memRealloc(_vertices, newCapacity);
            }
        }

        /** Column-major copy of the (row,col) view-projection into {@link #_mvp} for {@code glUniformMatrix4fv}. */
        private void captureMvp( Mat4F64 vp ) {
            for ( int col = 0; col < 4; col++ )
                for ( int row = 0; row < 4; row++ )
                    _mvp[col * 4 + row] = (float) vp.get(row, col);
        }
    }

    private static int linkProgram( String vertexSource, String fragmentSource ) {
        int vertex = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glLinkProgram(program);
        if ( GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE )
            throw new IllegalStateException("Shader link failed: " + GL20.glGetProgramInfoLog(program));
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        return program;
    }

    private static int compileShader( int type, String source ) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if ( GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE )
            throw new IllegalStateException("Shader compile failed: " + GL20.glGetShaderInfoLog(shader));
        return shader;
    }
}