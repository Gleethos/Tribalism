package dal.api;

import java.util.List;

public interface Query<M extends Model<M>> {

    List<M> asList();

}
