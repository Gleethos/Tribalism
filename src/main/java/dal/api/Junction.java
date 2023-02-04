package dal.api;

import sprouts.Val;

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
    /**
     *  Appends an AND operator to the query and accepts
     *  a selector for selecting another model property which
     *  should be included in the where clause.
     *
     * @param selector A selector function which receives a dummy model instance
     *                 for selecting and returning the desired property.
     * @return The next step in the fluent builder API, which defines a
     *         comparison between the here selected property and something else.
     * @param <T> The value/item type of the selected property.
     */
    <T> Compare<M, T> and( Function<M, Val<T>> selector );

    /**
     *  Appends an OR operator to the query and accepts
     *  a selector for selecting another model property which
     *  should be included in the where clause.
     *
     * @param selector A selector function which receives a dummy model instance
     *                 for selecting and returning the desired property.
     * @return The next step in the fluent builder API, which defines a
     *         comparison between the here selected property and something else.
     * @param <T> The value/item type of the selected property.
     */
    <T> Compare<M, T> or( Function<M, Val<T>> selector );

    /**
     *  Appends an AND operator to the query and accepts
     *  a selector for selecting another model property which
     *  should be included in the where clause.
     *
     * @param field The class of a custom model property subtype
     *              for selecting desired property of a model.
     * @return The next step in the fluent builder API, which defines a
     *         comparison between the here selected property and something else.
     * @param <T> The value/item type of the selected property.
     */
    <T> Compare<M, T> and( Class<? extends Val<T>> field );

    /**
     *  Appends an OR operator to the query and accepts
     *  a selector for selecting another model property which
     *  should be included in the where clause.
     *
     * @param field The class of a custom model property subtype
     *              for selecting desired property of a model.
     * @return The next step in the fluent builder API, which defines a
     *         comparison between the here selected property and something else.
     * @param <T> The value/item type of the selected property.
     */
    <T> Compare<M, T> or( Class<? extends Val<T>> field );

    /**
     *  Finished the where clause and defines that the query result should be
     *  sorted in ascending order by the specified field.
     *
     * @param selector The selector defining by which property the result should be sorted in ascending order.
     * @return The final fluent builder API which defines how the result should be returned.
     */
    <N extends Number> Query<M> orderAscendingBy( Function<M, Val<N>> selector );

    /**
     *  Finished the where clause and defines that the query result should be
     *  sorted in descending order by the specified field.
     *
     * @param selector The selector defining by which property the result should be sorted in descending order.
     * @return The final fluent builder API which defines how the result should be returned.
     */
    <N extends Number> Query<M> orderDescendingBy( Function<M, Val<N>> selector );

    /**
     *  Finished the where clause and defines that the query result should be
     *  sorted in ascending order by the specified field.
     *
     * @param field The field by which the result should be sorted in ascending order.
     * @return The final fluent builder API which defines how the result should be returned.
     */
    Query<M> orderAscendingBy( Class<? extends Val<?>> field );

    /**
     *  Finished the where clause and defines that the query result should be
     *  sorted in descending order by the specified field.
     *
     * @param field The field by which the result should be sorted in descending order.
     * @return The final fluent builder API which defines how the result should be returned.
     */
    Query<M> orderDescendingBy( Class<? extends Val<?>> field );

}
