package dal.api;

import java.util.List;
import java.util.Set;

public interface Query<M extends Model<M>> {

    List<M> asList();

    default Set<M> asSet() {
        return Set.copyOf(asList());
    }

}
