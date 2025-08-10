package dal.api;

/**
 * Represents a value in the {@link DataBase}, which is
 * expected to be immutable.
 **/
public non-sealed interface Value extends DataBaseEntity
{
    /**
     * {@inheritDoc}
     */
    boolean equals(Object o);

    /**
     * {@inheritDoc}
     */
    int hashCode();
}
