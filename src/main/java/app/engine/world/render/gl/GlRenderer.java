package app.engine.world.render.gl;

import app.engine.primitives.Mat4F64;
import app.engine.primitives.VecF64;
import app.engine.world.ScreenId;
import app.engine.world.World;
import app.engine.world.WorldSector;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
 *  <b>This milestone (3): retained per-block geometry.</b> The expensive geometry &mdash;
 *  the greedy-meshed full-detail voxel blocks &mdash; is uploaded to a per-block VBO and
 *  <i>kept</i> on the GPU, keyed by the immutable {@link WorldSector} that produced it
 *  (the GPU mirror of {@link SectorMeshCache}). Because the world tree is persistent and
 *  structurally shared, the same block instance recurs frame after frame and hits the
 *  cache &mdash; so static terrain is never re-meshed or re-uploaded. The per-frame
 *  {@link World#collectSectorsForRendering} walk (which already does frustum/occlusion/LoD
 *  culling) drives it: a {@linkplain SectorGeometry#isMeshBlock mesh block} draws its
 *  cached VBO, while the cheap coarse LoD boxes (a handful of quads) go into one small
 *  per-frame dynamic buffer. Blocks not drawn for {@link #EVICT_AFTER_FRAMES} frames are
 *  freed ({@code glDeleteBuffers}), bounding GPU memory as the camera roams.
 *  <p>
 *  <b>Threading:</b> a single {@code gl-renderer} thread owns rendering and calls
 *  {@link AWTGLCanvas#render()} on each viewport in turn (each call makes that viewport's
 *  context current and runs {@code initGL}/{@code paintGL}); GL object creation, drawing
 *  and deletion therefore all happen on this thread, with a context current. A
 *  {@code World} is a deeply immutable value, so {@link #setWorld} hands it across threads
 *  without locking. The shared mesh cache is touched only on this thread.
 */
public final class GlRenderer implements Renderer
{
    private static final float SKY_R = 135f / 255f;
    private static final float SKY_G = 180f / 255f;
    private static final float SKY_B = 235f / 255f;
    private static final long FRAME_MILLIS = 8L; // render-thread pacing (~120 Hz cap)
    private static final double REFINE_THRESHOLD_PX = 28.0;
    private static final VecF64 LIGHT = VecF64.of(-0.4, -1.0, -0.3).normalize();

    /** A cached per-block VBO is freed once it has not been drawn for this many frames. */
    private static final int EVICT_AFTER_FRAMES = 240;

    private static final int FLOATS_PER_VERTEX = 6; // x,y,z, r,g,b
    private static final int VERTICES_PER_QUAD = 6; // two triangles
    private static final int FLOATS_PER_QUAD = FLOATS_PER_VERTEX * VERTICES_PER_QUAD;

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
    private final SectorMeshCache _meshCache = new SectorMeshCache(); // CPU meshes; gl-renderer thread only
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

    /** A retained GPU upload of one detail block's mesh: its own VAO + VBO and how stale it is. */
    private static final class GpuChunk
    {
        final int vao;
        final int vbo;
        final int vertexCount;
        long lastUsedFrame;

        GpuChunk( int vao, int vbo, int vertexCount, long frame ) {
            this.vao = vao;
            this.vbo = vbo;
            this.vertexCount = vertexCount;
            this.lastUsedFrame = frame;
        }

        void free() {
            GL15.glDeleteBuffers(vbo);
            GL30.glDeleteVertexArrays(vao);
        }
    }

    /**
     *  One heavyweight GL canvas displaying a single screen. {@link #initGL}/{@link #paintGL}
     *  run on the {@code gl-renderer} thread, where the canvas's context is current.
     */
    private final class Viewport extends AWTGLCanvas
    {
        private final ScreenId _screenId;

        private int _program;
        private int _mvpLocation;
        private int _coarseVao; // re-uploaded each frame: the cheap LoD boxes
        private int _coarseVbo;
        private FloatBuffer _coarseVertices = MemoryUtil.memAllocFloat(FLOATS_PER_QUAD * 1024);
        private FloatBuffer _scratch = MemoryUtil.memAllocFloat(FLOATS_PER_QUAD * 1024); // building a chunk before upload

        private final Map<WorldSector, GpuChunk> _chunks = new HashMap<>(); // retained detail-block VBOs
        private final List<GpuChunk> _toDraw = new ArrayList<>();           // chunks drawn this frame

        private final float[] _mvp = new float[16];
        private boolean _mvpReady;
        private int _coarseQuadCount;
        private long _frame;

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

            _coarseVao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(_coarseVao);
            _coarseVbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, _coarseVbo);
            configureVertexFormat();
            GL30.glBindVertexArray(0);
        }

        @Override
        public void paintGL() {
            GL11.glViewport(0, 0, getFramebufferWidth(), getFramebufferHeight());
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            World world = _world.get();
            int faces = 0;
            int occlusionCulled = 0;
            if ( world != null ) {
                _frame++;
                _coarseVertices.clear();
                _coarseQuadCount = 0;
                _toDraw.clear();
                _mvpReady = false;

                World.RenderStats stats = world.collectSectorsForRendering(
                        _screenId, REFINE_THRESHOLD_PX,
                        ( sector, wantsDetail, view ) -> {
                            if ( !_mvpReady ) {
                                captureMvp(view.viewProjection());
                                _mvpReady = true;
                            }
                            if ( SectorGeometry.isMeshBlock(sector, wantsDetail) ) {
                                GpuChunk chunk = chunkFor(sector);
                                chunk.lastUsedFrame = _frame;
                                _toDraw.add(chunk);
                            } else {
                                SectorGeometry.emit(sector, wantsDetail, _meshCache, this::appendCoarseQuad);
                            }
                        });
                occlusionCulled = stats.occlusionCulledSectors();

                if ( _mvpReady ) {
                    GL20.glUseProgram(_program);
                    GL20.glUniformMatrix4fv(_mvpLocation, false, _mvp);

                    if ( _coarseQuadCount > 0 ) {
                        GL30.glBindVertexArray(_coarseVao);
                        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, _coarseVbo);
                        _coarseVertices.flip();
                        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, _coarseVertices, GL15.GL_DYNAMIC_DRAW);
                        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, _coarseQuadCount * VERTICES_PER_QUAD);
                        faces += _coarseQuadCount;
                    }
                    for ( GpuChunk chunk : _toDraw ) {
                        GL30.glBindVertexArray(chunk.vao);
                        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, chunk.vertexCount);
                        faces += chunk.vertexCount / VERTICES_PER_QUAD;
                    }
                    GL30.glBindVertexArray(0);
                }
                evictStaleChunks();
            }
            _stats.put(_screenId, new FrameStats(faces, occlusionCulled));
            swapBuffers();
        }

        /** Returns the retained VBO for a detail block, uploading it once on first sight. */
        private GpuChunk chunkFor( WorldSector block ) {
            GpuChunk chunk = _chunks.get(block);
            if ( chunk == null ) {
                chunk = uploadChunk(block);
                _chunks.put(block, chunk);
            }
            return chunk;
        }

        private GpuChunk uploadChunk( WorldSector block ) {
            int quadCount = _meshCache.meshOf(block).quads().size();
            ensureScratch(quadCount * FLOATS_PER_QUAD);
            for ( Quad quad : _meshCache.meshOf(block).quads() )
                putQuad(_scratch, quad);
            _scratch.flip();

            int vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);
            int vbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, _scratch, GL15.GL_STATIC_DRAW);
            configureVertexFormat();
            GL30.glBindVertexArray(0);
            return new GpuChunk(vao, vbo, quadCount * VERTICES_PER_QUAD, _frame);
        }

        private void evictStaleChunks() {
            long cutoff = _frame - EVICT_AFTER_FRAMES;
            Iterator<GpuChunk> it = _chunks.values().iterator();
            while ( it.hasNext() ) {
                GpuChunk chunk = it.next();
                if ( chunk.lastUsedFrame < cutoff ) {
                    chunk.free();
                    it.remove();
                }
            }
        }

        private void appendCoarseQuad( Quad quad ) {
            if ( _coarseVertices.remaining() < FLOATS_PER_QUAD ) {
                int newCapacity = Math.max(_coarseVertices.capacity() * 2, _coarseVertices.capacity() + FLOATS_PER_QUAD);
                _coarseVertices = MemoryUtil.memRealloc(_coarseVertices, newCapacity);
            }
            putQuad(_coarseVertices, quad);
            _coarseQuadCount++;
        }

        private void ensureScratch( int floats ) {
            if ( _scratch.capacity() < floats )
                _scratch = MemoryUtil.memRealloc(_scratch, Math.max(_scratch.capacity() * 2, floats));
            _scratch.clear();
        }

        /** Column-major copy of the (row,col) view-projection into {@link #_mvp} for {@code glUniformMatrix4fv}. */
        private void captureMvp( Mat4F64 vp ) {
            for ( int col = 0; col < 4; col++ )
                for ( int row = 0; row < 4; row++ )
                    _mvp[col * 4 + row] = (float) vp.get(row, col);
        }
    }

    /** Appends a quad as two triangles (c0,c1,c2 / c0,c2,c3), its flat shade baked into the colour. */
    private static void putQuad( FloatBuffer buffer, Quad quad ) {
        Color color = Shading.shade(TexturePalette.colorOf(quad.profile()), quad.normal(), LIGHT);
        float r = color.getRed()   / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue()  / 255f;
        putVertex(buffer, quad.c0(), r, g, b);
        putVertex(buffer, quad.c1(), r, g, b);
        putVertex(buffer, quad.c2(), r, g, b);
        putVertex(buffer, quad.c0(), r, g, b);
        putVertex(buffer, quad.c2(), r, g, b);
        putVertex(buffer, quad.c3(), r, g, b);
    }

    private static void putVertex( FloatBuffer buffer, VecF64 p, float r, float g, float b ) {
        buffer.put((float) p.x()).put((float) p.y()).put((float) p.z()).put(r).put(g).put(b);
    }

    /** Declares the interleaved {@code (vec3 position, vec3 colour)} layout on the bound VAO/VBO. */
    private static void configureVertexFormat() {
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 3L * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);
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