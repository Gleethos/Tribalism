package app.engine;

import app.engine.primitives.BoundingBox;
import app.engine.primitives.VecF64;
import app.engine.renderer.EntityRenderer;
import app.engine.renderer.TreeRenderer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class Engine
{
    Logger log = org.slf4j.LoggerFactory.getLogger(Engine.class);

    private final EngineContext _context;
    private final TreeRoot     _tree;
    private final TreeRenderer _renderer;

    private final List<View> _views = new ArrayList<>();

    public Engine(
        EngineContext context,
        EntityRenderer entityRenderer
    ) {
        _context  = context;
        _tree     = TreeRoot.of(BoundingBox.of(VecF64.of(-10, -10, -10), VecF64.of(10, 10, 10)));
        _renderer = TreeRenderer.of(entityRenderer);
        _views.add(new View());
    }

    public void addView( View view ) {
        _views.add(view);
    }

    public void removeView( View view ) {
        _views.remove(view);
    }

    public void update() {

    }

    public void render() {
        for ( View view : _views ) {
            try {
                _renderer.render(_tree, view);
            } catch ( Exception e ) {
                log.error("Error rendering view, '" + view + "'!", e);
            }
        }
    }

    private static void main(String... args) {

    }

}
