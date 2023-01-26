package dal.api;

import java.util.List;

public interface Get<M extends Model<M>> {

    List<M> toList();

}
