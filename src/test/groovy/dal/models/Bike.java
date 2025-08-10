package dal.models;

public interface Bike extends dal.api.Model<Bike>
{
    interface Brand extends sprouts.Var<String> {}
    interface Model extends sprouts.Var<String> {}
    interface Year extends sprouts.Var<String> {}
    Brand brand();
    Model model();
    Year year();
}
