package dal.models;

public interface Train extends dal.api.Model<Train>
{
    interface Name extends sprouts.Var<String> {}
    interface Waggons extends sprouts.Var<Integer> {}
    interface Speed extends sprouts.Var<Float> {}
    interface IsElectric extends sprouts.Var<Boolean> {}
    Name name();
    Waggons waggons();
    Speed speed();
    IsElectric isElectric();
}
