package dal.api;

import swingtree.api.mvvm.Val;

public interface Where<M extends Model<M>> extends Query<M> {

    <T> Compare<M, T> where( Class<? extends Val<T>> field );

}
