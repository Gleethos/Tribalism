package app.engine.world.render.gl;

import app.engine.primitives.Mat4F64;
import app.engine.primitives.VecF64;
import app.engine.world.ScreenId;
import app.engine.world.TextureProfile;
import app.engine.world.World;
import app.engine.world.WorldSector;
import app.engine.world.render.FrameStats;
import app.engine.world.render.Quad;
import app.engine.world.render.Renderer;
import app.engine.world.render.SectorGeometry;
import app.engine.world.render.SectorMeshCache;
import app.engine.world.render.Shading;
import app.engine.world.render.TextureBaker;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
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
import static org.lwjgl.opengl.GL.getCapabilities;

/**
 *  The OpenGL rendering backend (behind the {@link Renderer} SPI), drawing the world
 *  directly into a heavyweight {@code AWTGLCanvas} &mdash; no read-back, a real depth
 *  buffer, retained per-chunk geometry, and <b>procedural textures</b>.
 *  <p>
 *  <b>Texturing.</b> Each distinct appearance ({@link TextureProfile}) is baked once by
 *  {@link TextureBaker} into a seamless tile and uploaded into one layer of a
 *  {@code GL_TEXTURE_2D_ARRAY} (so a single texture is bound for the whole frame). Vertices
 *  carry world-space UVs taken from the quad's in-plane axes, divided by
 *  {@link #TILE_WORLD_SIZE}, and {@code GL_REPEAT} tiles the layer across the surface
 *  continuously (adjacent quads line up because the UVs are absolute world coordinates).
 *  The flat directional {@link Shading#brightness shade} rides along as a per-vertex scalar
 *  that the fragment shader multiplies the sampled texel by. Layers are assigned lazily on
 *  the render thread the first time an appearance is seen, and cached.
 *  <p>
 *  <b>Mip pyramid (texture LoD).</b> The base tile is baked at a high resolution
 *  ({@link #TILE_PX}px) so close surfaces stay crisp, and a full mip chain is box-filtered
 *  down from it ({@code glGenerateMipmap}) so distant surfaces sample a pre-averaged level
 *  instead of aliasing the high-frequency noise &mdash; the texture analogue of the retained
 *  geometry LoD. Minification is trilinear ({@code GL_LINEAR_MIPMAP_LINEAR}); where the
 *  driver offers it, anisotropic filtering keeps ground planes sharp at grazing angles. The
 *  chain is regenerated whenever a new appearance is baked into a layer (rare: once per
 *  appearance), since {@code glGenerateMipmap} rebuilds every layer from level 0.
 *  <p>
 *  <b>Retained chunk meshes.</b> {@link World#collectSectorsForRendering} hands down
 *  chunk-sized render units; each is greedy-meshed by {@link SectorMeshCache}, uploaded to
 *  its own VBO, and kept on the GPU keyed by the immutable {@link WorldSector} (structural
 *  sharing ⇒ static terrain is never re-meshed or re-uploaded). Chunks unused for
 *  {@link #EVICT_AFTER_FRAMES} frames are freed.
 *  <p>
 *  <b>Threading:</b> a single {@code gl-renderer} thread owns rendering (and all GL object
 *  creation/upload/deletion), via {@link AWTGLCanvas#render()} per viewport. A {@code World}
 *  is a deeply immutable value, so {@link #setWorld} crosses threads without locking.
 */
public final class GlRenderer implements Renderer
{
    private static final float SKY_R = 135f / 255f;
    private static final float SKY_G = 180f / 255f;
    private static final float SKY_B = 235f / 255f;
    private static final long FRAME_MILLIS = 8L; // render-thread pacing (~120 Hz cap)

    /** World-space edge size at or below which a sub-tree is meshed as one retained chunk. */
    private static final double CHUNK_SIZE = 64.0;
    /** World-space edge over which one texture tile repeats. */
    private static final double TILE_WORLD_SIZE = 8.0;
    private static final VecF64 LIGHT = VecF64.of(-0.4, -1.0, -0.3).normalize();

    /** A cached chunk VBO is freed once it has not been drawn for this many frames. */
    private static final int EVICT_AFTER_FRAMES = 240;

    private static final int TILE_PX = 256;    // baked texture-tile resolution (level 0 of the mip chain)
    private static final int MAX_LAYERS = 64;  // distinct appearances the texture array holds
    private static final float MAX_ANISOTROPY = 8f; // capped against the driver's limit

    private static final int FLOATS_PER_VERTEX = 7; // x,y,z, u,v, layer, brightness
    private static final int VERTICES_PER_QUAD = 6; // two triangles
    private static final int FLOATS_PER_QUAD = FLOATS_PER_VERTEX * VERTICES_PER_QUAD;

    private static final String VERTEX_SHADER =
            "#version 330 core\n" +
            "layout(location=0) in vec3 aPos;\n" +
            "layout(location=1) in vec2 aUv;\n" +
            "layout(location=2) in vec2 aAux;\n" + // x = texture layer, y = brightness
            "uniform mat4 uMvp;\n" +
            "out vec2 vUv;\n" +
            "flat out float vLayer;\n" +
            "out float vBright;\n" +
            "void main() {\n" +
            "    gl_Position = uMvp * vec4(aPos, 1.0);\n" +
            "    vUv = aUv;\n" +
            "    vLayer = aAux.x;\n" +
            "    vBright = aAux.y;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 330 core\n" +
            "in vec2 vUv;\n" +
            "flat in float vLayer;\n" +
            "in float vBright;\n" +
            "uniform sampler2DArray uTex;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    vec4 texel = texture(uTex, vec3(vUv, vLayer));\n" +
            "    fragColor = vec4(texel.rgb * vBright, 1.0);\n" +
            "}\n";

    private final AtomicReference<@Nullable World> _world = new AtomicReference<>();
    private final Map<ScreenId, Viewport> _viewports = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Viewport> _canvases = new CopyOnWriteArrayList<>();
    /** Distinct render-failure signatures already reported (render thread only; see {@link #renderLoop}). */
    private final java.util.Set<String> _reportedRenderFailures = new java.util.HashSet<>();
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
                } catch ( RuntimeException ex ) {
                    // The canvas can momentarily be un-renderable (resize/reparent), so skipping a frame
                    // is fine - but report each DISTINCT failure once: a deterministic per-frame throw
                    // otherwise freezes the picture with no trace whatsoever (a meshing bug hid behind
                    // this catch as two "frozen spots on the horizon" for days).
                    String signature = ex.getClass().getName() + ": " + ex.getMessage();
                    if ( _reportedRenderFailures.add(signature) )
                        ex.printStackTrace();
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
     *  The identity of a retained chunk upload: the (immutable) sector <i>and</i> the level of detail
     *  it was meshed at. The same sector drawn at two distances needs two meshes, so the resolution is
     *  part of the key; structural sharing still makes the (sector, res) pair stable frame-to-frame.
     */
    private record ChunkKey( WorldSector sector, int resolution ) {}

    /** A retained GPU upload of one chunk's mesh: its own VAO + VBO and how stale it is. */
    private static final class GpuChunk
    {
        final int vao;
        final int vbo;
        final int vertexCount;
        long lastUsedFrame;
        /** The exact key instance this chunk is stored under (see {@code chunkFor}'s key refresh). */
        ChunkKey storedKey;

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
        private int _texLocation;
        private int _textureArray;
        private final Map<TextureProfile, Integer> _layers = new HashMap<>(); // appearance -> texture-array layer
        private FloatBuffer _scratch = MemoryUtil.memAllocFloat(FLOATS_PER_QUAD * 1024); // building a chunk before upload

        private final Map<ChunkKey, GpuChunk> _chunks = new HashMap<>(); // retained chunk VBOs, keyed by (sector, LoD)
        private final List<GpuChunk> _toDraw = new ArrayList<>();        // chunks drawn this frame

        private final float[] _mvp = new float[16];
        private boolean _mvpReady;
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
            // Back-face culling: the mesher emits only outward-facing shell quads, all wound counter-clockwise
            // seen from outside (see Cubes.FACE_CORNERS), so the hidden back side of every face is dropped.
            // This roughly halves the submitted triangles and stops the insides of meshes showing through when
            // the camera flies within them. (If a visual check shows it culling the wrong side, flip to GL_CW.)
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_BACK);
            GL11.glFrontFace(GL11.GL_CCW);

            _program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            _mvpLocation = GL20.glGetUniformLocation(_program, "uMvp");
            _texLocation = GL20.glGetUniformLocation(_program, "uTex");

            _textureArray = GL11.glGenTextures();
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, _textureArray);
            GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, GL11.GL_RGBA8, TILE_PX, TILE_PX, MAX_LAYERS,
                              0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            // Trilinear minification over the box-filtered mip chain; crisp nearest-neighbour-free magnification.
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            if ( getCapabilities().GL_EXT_texture_filter_anisotropic ) {
                float limit = GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
                GL11.glTexParameterf(GL30.GL_TEXTURE_2D_ARRAY,
                        EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                        Math.min(MAX_ANISOTROPY, limit));
            }
            // Make the texture mip-complete up front, so it samples cleanly before any layer is baked.
            GL30.glGenerateMipmap(GL30.GL_TEXTURE_2D_ARRAY);
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
                _toDraw.clear();
                _mvpReady = false;

                World.RenderStats stats = world.collectSectorsForRendering(
                        _screenId, CHUNK_SIZE,
                        ( sector, view, meshResolution ) -> {
                            if ( !_mvpReady ) {
                                captureMvp(view.viewProjection());
                                _mvpReady = true;
                            }
                            GpuChunk chunk = chunkFor(sector, meshResolution);
                            chunk.lastUsedFrame = _frame;
                            _toDraw.add(chunk);
                        });
                occlusionCulled = stats.occlusionCulledSectors();

                if ( _mvpReady ) {
                    GL20.glUseProgram(_program);
                    GL20.glUniformMatrix4fv(_mvpLocation, false, _mvp);
                    GL13.glActiveTexture(GL13.GL_TEXTURE0);
                    GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, _textureArray);
                    GL20.glUniform1i(_texLocation, 0);
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

        /** Returns the retained VBO for a chunk at a level of detail, meshing and uploading it once on first sight. */
        private GpuChunk chunkFor( WorldSector chunk, int meshResolution ) {
            ChunkKey key = new ChunkKey(chunk, meshResolution);
            GpuChunk gpu = _chunks.get(key);
            if ( gpu == null ) {
                gpu = uploadChunk(chunk, meshResolution);
                gpu.storedKey = key;
                _chunks.put(key, gpu);
            } else if ( gpu.storedKey.sector() != chunk ) {
                // The world stream produced a NEW (value-equal) sector instance for this chunk. A plain
                // HashMap keeps the OLD key on a hit, so every later frame's lookup would re-run the
                // DEEP value-equality between the two instances (a whole-subtree walk per chunk per
                // frame, profiled as a major frame cost). Re-store under the live instance once, so
                // lookups go back to identity-fast.
                _chunks.remove(gpu.storedKey);
                gpu.storedKey = key;
                _chunks.put(key, gpu);
            }
            return gpu;
        }

        private GpuChunk uploadChunk( WorldSector chunk, int meshResolution ) {
            List<Quad> quads = new ArrayList<>();
            SectorGeometry.emitChunk(chunk, _meshCache, meshResolution, quads::add);
            ensureScratch(quads.size() * FLOATS_PER_QUAD);
            for ( Quad quad : quads )
                putQuad(quad);
            _scratch.flip();

            int vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);
            int vbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, _scratch, GL15.GL_STATIC_DRAW);
            configureVertexFormat();
            GL30.glBindVertexArray(0);
            return new GpuChunk(vao, vbo, quads.size() * VERTICES_PER_QUAD, _frame);
        }

        /** Appends a quad as two triangles, with world-space UVs, its appearance's layer, and its baked shade. */
        private void putQuad( Quad quad ) {
            int layer = layerFor(quad.profile());
            float brightness = (float) Shading.brightness(quad.normal(), LIGHT);
            int axis = dominantAxis(quad.normal());
            int u = otherAxis(axis, 0), v = otherAxis(axis, 1);
            putVertex(quad.c0(), u, v, layer, brightness);
            putVertex(quad.c1(), u, v, layer, brightness);
            putVertex(quad.c2(), u, v, layer, brightness);
            putVertex(quad.c0(), u, v, layer, brightness);
            putVertex(quad.c2(), u, v, layer, brightness);
            putVertex(quad.c3(), u, v, layer, brightness);
        }

        private void putVertex( VecF64 p, int u, int v, int layer, float brightness ) {
            _scratch.put((float) p.x()).put((float) p.y()).put((float) p.z());
            _scratch.put((float) (component(p, u) / TILE_WORLD_SIZE)).put((float) (component(p, v) / TILE_WORLD_SIZE));
            _scratch.put((float) layer).put(brightness);
        }

        /** The texture-array layer for an appearance, baking and uploading it on first use (cached). */
        private int layerFor( TextureProfile profile ) {
            // Key (and bake) by the appearance BUCKET, not the raw profile. Aggregated far-field faces
            // are a continuum of slightly different profiles (and carry geometric insets); keying them
            // raw flooded all MAX_LAYERS slots with visually identical ~50ms bakes - a frame hitch per
            // streamed-in sector - and then pushed everything else onto the layer-0 fallback.
            // Four steps per quality: coarse enough that aggregated blends collapse onto few buckets
            // (and their many faint qualities round to zero, which also makes each bake cheap - the
            // baker evaluates one noise pattern per PRESENT quality per pixel), fine enough that the
            // pure authored materials keep distinct looks.
            TextureProfile appearance = profile.bucketed(4);
            Integer layer = _layers.get(appearance);
            if ( layer != null )
                return layer;
            if ( _layers.size() >= MAX_LAYERS )
                return 0; // out of layers: fall back to the first appearance rather than fail
            int assigned = _layers.size();
            uploadLayer(assigned, TextureBaker.bake(appearance, TILE_PX));
            _layers.put(appearance, assigned);
            return assigned;
        }

        private void uploadLayer( int layer, int[] argb ) {
            ByteBuffer pixels = MemoryUtil.memAlloc(argb.length * 4);
            for ( int p : argb )
                pixels.put((byte) ((p >> 16) & 0xFF))  // R
                      .put((byte) ((p >> 8) & 0xFF))   // G
                      .put((byte) (p & 0xFF))          // B
                      .put((byte) ((p >>> 24) & 0xFF)); // A
            pixels.flip();
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, _textureArray);
            GL12.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer, TILE_PX, TILE_PX, 1,
                                 GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            MemoryUtil.memFree(pixels);
            // Rebuild the mip chain so this layer's distant LoDs are box-filtered, not aliased.
            GL30.glGenerateMipmap(GL30.GL_TEXTURE_2D_ARRAY);
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

    /** @return The axis (0=x, 1=y, 2=z) the normal points most strongly along. */
    private static int dominantAxis( VecF64 normal ) {
        double ax = Math.abs(normal.x()), ay = Math.abs(normal.y()), az = Math.abs(normal.z());
        if ( ax >= ay && ax >= az ) return 0;
        if ( ay >= az ) return 1;
        return 2;
    }

    /** @return The {@code which}-th (0 or 1) axis other than {@code a}, in ascending order. */
    private static int otherAxis( int a, int which ) {
        int[] others = a == 0 ? new int[]{ 1, 2 } : a == 1 ? new int[]{ 0, 2 } : new int[]{ 0, 1 };
        return others[which];
    }

    private static double component( VecF64 p, int axis ) {
        return axis == 0 ? p.x() : (axis == 1 ? p.y() : p.z());
    }

    /** Declares the interleaved {@code (vec3 position, vec2 uv, vec2 layer+brightness)} layout on the bound VAO/VBO. */
    private static void configureVertexFormat() {
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 3L * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, stride, 5L * Float.BYTES);
        GL20.glEnableVertexAttribArray(2);
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