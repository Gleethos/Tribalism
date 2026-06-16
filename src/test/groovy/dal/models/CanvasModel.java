package dal.models;

import dal.api.Model;
import dal.values.Shape;
import sprouts.Tuple;
import sprouts.Var;

/** A model holding a whole list (palette) of polymorphic sum-typed shapes. */
public interface CanvasModel extends Model<CanvasModel> {
    interface Title extends Var<String> {}
    interface Shapes extends Var<Tuple<Shape>> {}
    Title title();
    Shapes shapes();
}
