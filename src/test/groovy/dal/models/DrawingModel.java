package dal.models;

import dal.api.Model;
import dal.values.Shape;
import sprouts.Var;

/** A model holding a polymorphic sum-typed value (its shape) plus a plain label. */
public interface DrawingModel extends Model<DrawingModel> {
    Var<String> label();
    Var<Shape> shape();
}
