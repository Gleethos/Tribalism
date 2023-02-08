package dal.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 *  The last step in the fluent query builder API of the {@link DataBase}
 *  which defines in what form the result of the query should be returned.
 *  Usually the result is a simple list of models, but there are also alternative
 *  methods, which are very useful.
 *  For example, if the query is supposed to return exactly a single item, the
 *  {@link #expectOne()} method should be used, on the other hand if the query
 *  is supposed to return one or no items, the {@link #expectOneOrNone()} method.
 *
 * @param <M> The type of the model to query.
 */
public interface Query<M extends Model<M>>
{
    /**
     *  Returns the result of the query as a list of models.
     *  If the query is supposed to return multiple items you may also use the
     *  {@link #asSet()} method.
     *
     * @return The result of the query as a list of models.
     */
    List<M> asList();

    /**
     *  Returns the result of the query as a set of models.
     *  If the query is supposed to return multiple items you may also use the
     *  {@link #asList()} method.
     *
     * @return The result of the query as a set of models.
     */
    default Set<M> asSet() { return Set.copyOf(asList()); }

    /**
     *  Returns the result of the query as a single model or throws an exception
     *  if the query result is empty or contains more than one item.
     *
     * @return The result of the query as a single model.
     */
    default M expectOne() {
        List<M> list = asList();
        if ( list.size() != 1 )
            throw new IllegalStateException("Expected exactly one result, but got " + list.size());

        return list.get(0);
    }

    /**
     *  Returns the result of the query as a single model or throws an exception
     *  if the query result contains more than one item.
     *  If the query result is empty, an empty {@link Optional} is returned.
     *
     * @return The result of the query as a single model wrapped in an {@link Optional}, which
     *        is empty if the query result is empty.
     */
    default Optional<M> expectOneOrNone() {
        List<M> list = asList();
        if ( list.size() > 1 )
            throw new IllegalStateException("Expected at most one result, but got " + list.size());

        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     *  Returns the first item of the query result or if the
     *  query result is empty, an empty {@link Optional} is returned.
     *
     * @return The first item of the query result wrapped in an {@link Optional}, which
     *         is empty if the query result is empty.
     */
    default Optional<M> first() { return asList().stream().findFirst(); }

    /**
     *  Returns the last item of the query result or if the
     *  query result is empty, an empty {@link Optional} is returned.
     *
     * @return The last item of the query result wrapped in an {@link Optional}, which
     *         is empty if the query result is empty.
     */
    default Optional<M> last() { return asList().stream().reduce((first, second) -> second); }

    default List<M> limit(int limit) { return asList().subList(0, limit); }

    default List<M> skip(int skip) { return asList().subList(skip, asList().size()); }

    default List<M> skip(int skip, int limit) { return asList().subList(skip, skip + limit); }

    default int count() { return asList().size(); }

    default boolean exists() { return !asList().isEmpty(); }

    default boolean notExists() { return asList().isEmpty(); }
}
