package dal.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface Query<M extends Model<M>>
{
    List<M> asList();

    default Set<M> asSet() { return Set.copyOf(asList()); }

    default Optional<M> first() { return asList().stream().findFirst(); }

    default Optional<M> last() { return asList().stream().reduce((first, second) -> second); }

    default List<M> limit(int limit) { return asList().subList(0, limit); }

    default List<M> skip(int skip) { return asList().subList(skip, asList().size()); }

    default List<M> skip(int skip, int limit) { return asList().subList(skip, skip + limit); }

    default int count() { return asList().size(); }
}
