package dal.api;

import swingtree.api.mvvm.Val;

import java.util.List;
import java.util.function.Function;

/**
 *   A step in the fluent query builder API of the {@link DataBase}
 *   which defines a chain of boolean operations in the where clause
 *   of the query or simply returns the result of the query.
 *
 * @param <M> The type of the model to query.
 */
public interface Junction<M extends Model<M>> extends Query<M>
{
    <T> Compare<M, T> and( Function<M, Val<T>> selector );

    <T> Compare<M, T> or( Function<M, Val<T>> selector );

    <T> Compare<M, T> and(Class<? extends Val<T>> field);

    <T> Compare<M, T> or(Class<? extends Val<T>> field);

    Query<M> orderAscendingBy(Class<? extends Val<?>> field);

    Query<M> orderDescendingBy(Class<? extends Val<?>> field);

}
