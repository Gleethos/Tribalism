package dal.models;

import dal.api.Model;
import dal.values.Product;
import sprouts.Var;

import java.time.LocalDateTime;

public interface ProductModel extends Model<ProductModel> {

    Var<Product> state();
    default Var<String> name() { return state().zoomTo(Product::name, Product::withName); }
    default Var<String> description() { return state().zoomTo(Product::description, Product::withDescription); }
    default Var<LocalDateTime> createDate() { return state().zoomTo(Product::creationDate, Product::withCreationDate); }
    default Var<Double> price() { return state().zoomTo(Product::price, Product::withPrice); }

}
