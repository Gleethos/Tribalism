package app.engine.entities;

import app.engine.primitives.BoundingBox;
import app.engine.primitives.VecF64;
import swingtree.UI;

import javax.swing.ImageIcon;

public class IconEntity implements Entity
{
    private final ImageIcon _icon;
    private final VecF64      _position;
    private final BoundingBox _bounds;


    public IconEntity( String icon, VecF64 position, BoundingBox bounds ) {
        _icon     = UI.findIcon(icon).get();
        _position = position;
        _bounds   = bounds;
    }

    @Override
    public VecF64 position() { return _position; }

    @Override
    public BoundingBox bounds() { return _bounds; }

    @Override
    public Entity[] update() {
        return new Entity[]{this};
    }
}
