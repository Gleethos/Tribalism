package dal.api;

import swingtree.api.mvvm.Val;

import java.util.List;

public interface Junction<M extends Model<M>> extends Get<M> {

    <T> WhereField<M, T> and(Class<? extends Val<T>> field);

    <T> WhereField<M, T> or(Class<? extends Val<T>> field);

    default List<M> limit(int limit) {
        return toList().subList(0, limit);
    }

    Get<M> orderAscendingBy(Class<? extends Val<?>> field);

    Get<M> orderDescendingBy(Class<? extends Val<?>> field);

}
