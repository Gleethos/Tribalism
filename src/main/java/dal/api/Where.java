package dal.api;

import swingtree.api.mvvm.Val;

public interface Where<M extends Model<M>> extends Get<M> {

    <T> WhereField<M, T> where(Class<? extends Val<T>> field);

}
