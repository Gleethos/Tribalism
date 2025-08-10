package app.engine.renderer;

import app.engine.View;
import app.engine.entities.Entity;

public interface EntityRenderer
{
    void render( Entity entity, View view );
}
