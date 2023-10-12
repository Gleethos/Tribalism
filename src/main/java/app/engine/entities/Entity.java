package app.engine.entities;

import app.engine.primitives.BoundingBox;
import app.engine.primitives.VecF64;

/**
 *  Implementations of this ought to be immutable!
 */
public interface Entity
{
    default VecF64 position() { return bounds().center(); }

    BoundingBox bounds();

    /**
     * @return Usually a copy of the entity with the updated position
     *         or an empty array if the entity is destroyed.
     */
    Entity[] update(/*todo: context*/);
}
