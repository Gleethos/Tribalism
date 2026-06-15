package dal.api;

import sprouts.Val;

import java.util.List;
import java.util.function.Function;

/**
 *   A step in the fluent query builder API of the {@link DataBase}
 *   which defines a chain of boolean operations in the where clause
 *   of the query or simply returns the result of the query.<br>
 *   <br>
 *   So given the following example:
 *   <pre>{@code
 *     var foods = db.select(Food.class)
 *                 .where(Food::carbs)
 *                 .lessThan(50)
 *                 .and(Food::protein)
 *                 .greaterThan(20)
 *                 .or(Food::fat)
 *                 .lessThan(10)
 *                 .and(Food::calories)
 *                 .greaterThan(200)
 *                 .asList();
 *   }</pre>
 *   The {@link Junction} interface is used to
 *   combine {@link Compare} objects with the logical AND, and OR operators.
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
     *  Appends an AND operator and selects a field nested inside a {@link dal.api.Value} held by the
     *  model. See {@link Where#where(Function, Function)} for the semantics of the selectors.
     */
    <V, T> Compare<M, T> and( Function<M, Val<V>> rootSelector, Function<V, T> nested );

    /** Two-value-deep nested variant of {@link #and(Function, Function)}. */
    <V, A, T> Compare<M, T> and( Function<M, Val<V>> rootSelector, Function<V, A> nested1, Function<A, T> nested2 );

    /** Three-value-deep nested variant of {@link #and(Function, Function)}. */
    <V, A, B, T> Compare<M, T> and( Function<M, Val<V>> rootSelector, Function<V, A> nested1, Function<A, B> nested2, Function<B, T> nested3 );

    /**
     *  Appends an OR operator and selects a field nested inside a {@link dal.api.Value} held by the
     *  model. See {@link Where#where(Function, Function)} for the semantics of the selectors.
     */
    <V, T> Compare<M, T> or( Function<M, Val<V>> rootSelector, Function<V, T> nested );

    /** Two-value-deep nested variant of {@link #or(Function, Function)}. */
    <V, A, T> Compare<M, T> or( Function<M, Val<V>> rootSelector, Function<V, A> nested1, Function<A, T> nested2 );

    /** Three-value-deep nested variant of {@link #or(Function, Function)}. */
    <V, A, B, T> Compare<M, T> or( Function<M, Val<V>> rootSelector, Function<V, A> nested1, Function<A, B> nested2, Function<B, T> nested3 );

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

    /**
     *  Sorts ascending by a field nested inside a {@link dal.api.Value} held by the model, selected
     *  inline (no zoom method required), e.g.
     *  {@code orderAscendingBy(OrgModel::org, Org::rank)} or
     *  {@code orderAscendingBy(OrgModel::org, o -> o.place().location().lat())}.
     *  See {@link Where#where(Function, Function)} for the selector semantics.
     */
    <V, T> Query<M> orderAscendingBy( Function<M, Val<V>> rootSelector, Function<V, T> nested );

    /** Two-value-deep nested variant of {@link #orderAscendingBy(Function, Function)}. */
    <V, A, T> Query<M> orderAscendingBy( Function<M, Val<V>> rootSelector, Function<V, A> nested1, Function<A, T> nested2 );

    /** Three-value-deep nested variant of {@link #orderAscendingBy(Function, Function)}. */
    <V, A, B, T> Query<M> orderAscendingBy( Function<M, Val<V>> rootSelector, Function<V, A> nested1, Function<A, B> nested2, Function<B, T> nested3 );

    /**
     *  Sorts descending by a field nested inside a {@link dal.api.Value} held by the model, selected
     *  inline (no zoom method required). See {@link Where#where(Function, Function)} for the semantics.
     */
    <V, T> Query<M> orderDescendingBy( Function<M, Val<V>> rootSelector, Function<V, T> nested );

    /** Two-value-deep nested variant of {@link #orderDescendingBy(Function, Function)}. */
    <V, A, T> Query<M> orderDescendingBy( Function<M, Val<V>> rootSelector, Function<V, A> nested1, Function<A, T> nested2 );

    /** Three-value-deep nested variant of {@link #orderDescendingBy(Function, Function)}. */
    <V, A, B, T> Query<M> orderDescendingBy( Function<M, Val<V>> rootSelector, Function<V, A> nested1, Function<A, B> nested2, Function<B, T> nested3 );

}
