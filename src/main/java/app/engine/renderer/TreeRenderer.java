package app.engine.renderer;

import app.engine.TreeRoot;
import app.engine.View;

public class TreeRenderer
{
    public static TreeRenderer of( EntityRenderer entityRenderer ) {
        return new TreeRenderer(entityRenderer);
    }

    private final EntityRenderer _entityRenderer;

    private TreeRenderer( EntityRenderer entityRenderer ) {
        _entityRenderer = entityRenderer;
    }

    public void render(TreeRoot root, View view )
    {
        //root.forEach( _entityRenderer::render );
    }

}
