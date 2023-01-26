package dal.api;

import swingtree.api.mvvm.Val;

import java.util.function.Consumer;

public interface Model<M> {

    interface Id extends Val<Integer> {}

    Id id();

    void batch(Consumer<M> transaction);

}