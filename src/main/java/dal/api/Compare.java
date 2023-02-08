package dal.api;

import sprouts.Val;

import java.util.List;

/**
 *   A step in the fluent query builder API of the {@link DataBase}
 *   which defines the comparison of a previously defined field with a value.
 *
 * @param <M> The type of the model to query.
 * @param <T> The type of the field to compare.
 */
public interface Compare<M extends Model<M>, T>
{
    /**
     *  Checks if the selected field is equal to the given value.
     *
     * @param value The value to compare the selected field with.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    Junction<M> is( T value );

    /**
     * Checks if the selected field is equal to the value of the given property.
     * @param value The property whose item should be compared with the selected field.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    default Junction<M> is( Val<T> value ) { return is(value.get()); }

    /**
     *  Checks if the selected field is not equal to the given value.
     *
     * @param value The value to compare the selected field with.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    Junction<M> isNot( T value );

    /**
     * Checks if the selected field is not equal to the value of the given property.
     * @param value The property whose item should be compared with the selected field.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    default Junction<M> isNot( Val<T> value ) { return isNot(value.get()); }

    /**
     *  Checks if the selected field is like the given value.
     *  Like means that the given value is expected to be a string pattern which may contain
     *  the wildcard character '%' which matches any sequence of characters.
     *
     * @param value The value to compare the selected field with.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    Junction<M> like( T value );

    /**
     * Checks if the selected field is like the value of the given property.
     * Like means that the given value is a pattern which may contain
     * the wildcard character '%' which matches any sequence of characters.
     * @param value The property whose item should be compared with the selected field.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    default Junction<M> like( Val<T> value ) { return like(value.get()); }

    /**
     *  Checks if the selected field is not like the given value.
     *  Like means that the given value is expected to be a string pattern which may contain
     *  the wildcard character '%' which matches any sequence of characters.
     *
     * @param value The value to compare the selected field with.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    Junction<M> notLike( T value );

    /**
     * Checks if the selected field is not like the value of the given property.
     * Like means that the given value is a pattern which may contain
     * the wildcard character '%' which matches any sequence of characters.
     * @param value The property whose item should be compared with the selected field.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    default Junction<M> notLike( Val<T> value ) { return notLike(value.get()); }

    /**
     *  Checks if the selected field is within the provided array of arguments.
     *
     * @param values The values to compare the selected field with.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    Junction<M> in( T... values );

    /**
     * Checks if the selected field is within the provided array of property arguments.
     * @param values The properties whose items should be compared with the selected field.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    default Junction<M> in( Val<T>... values ) {
        T[] vals = (T[]) new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            vals[i] = values[i].get();
        }
        return in(vals);
    }

    /**
     * Checks if the value of the selected field is within the provided list.
     * @param values The list of values to compare the selected field with.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    default Junction<M> in( List<T> values ) {
        return in(values.toArray((T[]) new Object[values.size()]));
    }

    /**
     *  Checks if the selected field is NOT within the provided array of arguments.
     *
     * @param values The values to compare the selected field with.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    Junction<M> notIn(T... values);

    /**
     * Checks if the values of the selected field are NOT within the provided array of property arguments.
     * @param values The properties whose items should be compared with the selected field.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    default Junction<M> notIn( Val<T>... values ) {
        T[] vals = (T[]) new Object[values.length];
        for ( int i = 0; i < values.length; i++ ) {
            vals[i] = values[i].get();
        }
        return notIn(vals);
    }

    /**
     * Checks if the values of the selected field is NOT within the provided list.
     * @param values The list of values to compare the selected field with.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    default Junction<M> notIn( List<T> values ) {
        return notIn(values.toArray((T[]) new Object[values.size()]));
    }

    /**
     *  Checks if the values of the selected field are null.
     *
     *  @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    Junction<M> isNull();

    /**
     *  Checks if the values of the selected field are not null.
     *
     *  @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    Junction<M> isNotNull();

    /**
     *  Checks if the values of the selected field are greater than the given value.
     *
     *  @param value The value to compare the selected field with.
     *  @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    Junction<M> greaterThan( T value );

    /**
     * Checks if the values of the selected field are greater than the value of the given property.
     * @param value The property whose item should be compared with the selected field.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    default Junction<M> greaterThan( Val<T> value ) { return greaterThan(value.get()); }

    /**
     *  Checks if the values of the selected field are greater than or equal to the given value.
     *
     *  @param value The value to compare the selected field with.
     *  @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    Junction<M> greaterThanOrEqual( T value );

    /**
     * Checks if the values of the selected field are greater than or equal to the value of the given property.
     * @param value The property whose item should be compared with the selected field.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    default Junction<M> greaterThanOrEqual( Val<T> value ) { return greaterThanOrEqual(value.get()); }

    /**
     *  Checks if the values of the selected field are less than the given value.
     *
     *  @param value The value to compare the selected field with.
     *  @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    Junction<M> lessThan( T value );

    /**
     * Checks if the values of the selected field are less than the value of the given property.
     * @param value The property whose item should be compared with the selected field.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    default Junction<M> lessThan( Val<T> value ) { return lessThan(value.get()); }

    /**
     *  Checks if the values of the selected field are less than or equal to the given value.
     *
     *  @param value The value to compare the selected field with.
     *  @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    Junction<M> lessThanOrEqual( T value );

    /**
     * Checks if the values of the selected field are less than or equal to the value of the given property.
     * @param value The property whose item should be compared with the selected field.
     * @return A {@link Junction} object which allows to define a chain of boolean operations
     */
    default Junction<M> lessThanOrEqual( Val<T> value ) { return lessThanOrEqual(value.get()); }

}
