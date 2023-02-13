package dal.api;

import sprouts.Val;

import java.util.function.Function;

/**
 *  In the fluent query builder API of TopSoil,
 *  this interface is used to open the where clause of the query.
 *  <p>
 *  <b>Example:</b>
 *  <pre>{@code
 *      db.select(Food.class)
 *      .where(Food::name) // Select the field to be tested
 *      .is("Tofu")
 *      .and(Food::calories)
 *      .isLessThan(100)
 *  }</pre>
 *  <p>
 *      This will select all rows from the Food table where the name is "Tofu" and the calories are less than 100.
 *  <p>
 *  <b>Important:</b> Do not use a lambda expression here, use a method reference instead.
 *  Calling anything else than a method reference will result in a runtime exception.
 *  <p>
 * @param <M> The type of the model to query.
 */
public interface Where<M extends Model<M>> extends Query<M>
{
    /**
     *  Select the field to be tested in the where clause of the query
     *  using the method reference syntax like this:
     *  <pre>{@code
     *    db.select(MyModel.class)
     *      .where(MyModel::field)
     *      .is(42);
     *  }</pre>
     *
     * @param selector A selector function which selects the field to be tested.
     *                 Do not use a lambda expression here, use a method reference instead.
     *                 Calling anything else than a method reference will result in a runtime
     *                 exception.
     * @return A {@link Compare} object which allows to specify the comparison operator.
     * @param <T> The type of the field to be tested.
     */
    <T> Compare<M, T> where( Function<M, Val<T>> selector );

    /**
     *  Select the field to be tested in the where clause of the query
     *  by passing the field type as a parameter (if the return type id a custom property extension).
     *  <pre>{@code
     *    db.select(MyModel.class)
     *      .where(MyModel.MyCustomProperty.class)
     *      .is(42);
     * }</pre>
     * @param field The type of the field to be tested.
     * @return A {@link Compare} object which allows to specify the comparison operator.
     * @param <T> The type of the field to be tested.
     */
    <T> Compare<M, T> where( Class<? extends Val<T>> field );

}
