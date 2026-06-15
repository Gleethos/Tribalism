package dal.models;

import dal.api.Model;
import dal.values.Org;
import sprouts.Var;

/**
 *  A model whose entire state is a single {@link Org} value. Crucially it declares NO zoom
 *  delegation methods: nested fields are reached purely through the {@code where(root, lens..)}
 *  query API, e.g. {@code where(OrgModel::org, Org::place, Place::name)}.
 */
public interface OrgModel extends Model<OrgModel> {
    Var<Org> org();
}
