package dal.api;

import swingtree.api.mvvm.Val;

import java.util.function.Function;

public interface Where<M extends Model<M>> extends Query<M> {

    <T> Compare<M, T> where( Class<? extends Val<T>> field );

    <T> Compare<M, T> where( Function<M, Val<T>> selector );

}
