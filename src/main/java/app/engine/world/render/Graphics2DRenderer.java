package app.engine.world.render;

import app.engine.world.ScreenId;
import app.engine.world.World;
import org.jspecify.annotations.Nullable;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 *  The software rendering backend: it draws the world with {@link WorldRenderer} (a
 *  {@link Graphics2D} painter&mdash;back-face cull, project, painter's sort, fill) into a
 *  lightweight Swing {@link JPanel}. No GPU, no native libraries; it is the dependable
 *  fallback and the reference picture to A/B the GPU backend against.
 *  <p>
 *  It owns its own render loop (a Swing {@link Timer} repainting the viewports on the
 *  EDT) so the host only has to {@link #setWorld publish} the latest world. All drawing
 *  happens on the EDT, so the shared {@link WorldRenderer} (and its mesh cache) need no
 *  synchronization.
 */
public final class Graphics2DRenderer implements Renderer
{
    private static final int FRAME_MILLIS = 16; // ~60 FPS

    private final WorldRenderer _painter = new WorldRenderer();
    private final AtomicReference<@Nullable World> _world = new AtomicReference<>();
    private final Map<ScreenId, Component> _viewports = new ConcurrentHashMap<>();
    private final Map<ScreenId, FrameStats> _stats = new ConcurrentHashMap<>();
    private final Timer _loop;

    public Graphics2DRenderer() {
        _loop = new Timer(FRAME_MILLIS, e -> _viewports.values().forEach(Component::repaint));
        _loop.start();
    }

    @Override
    public Component viewportFor( ScreenId screenId ) {
        return _viewports.computeIfAbsent(screenId, Viewport::new);
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
        _loop.stop();
    }

    /** A lightweight panel that paints one screen's world view on the EDT. */
    private final class Viewport extends JPanel
    {
        private final ScreenId _screenId;

        Viewport( ScreenId screenId ) {
            _screenId = screenId;
            setBackground(Color.BLACK);
        }

        @Override
        protected void paintComponent( Graphics g ) {
            super.paintComponent(g);
            World world = _world.get();
            if ( world == null )
                return; // nothing published yet
            _painter.render((Graphics2D) g, world, _screenId);
            _stats.put(_screenId, new FrameStats(_painter.facesDrawn(), _painter.occlusionCulledSectors()));
        }
    }
}