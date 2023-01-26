package dal.api;

import swingtree.api.mvvm.Val;

import java.util.List;

public interface Junction<M extends Model<M>> extends Query<M> {

    <T> Compare<M, T> and(Class<? extends Val<T>> field);

    <T> Compare<M, T> or(Class<? extends Val<T>> field);

    default List<M> limit(int limit) {
        return asList().subList(0, limit);
    }

    Query<M> orderAscendingBy(Class<? extends Val<?>> field);

    Query<M> orderDescendingBy(Class<? extends Val<?>> field);

}
