package dal.api;

public interface Compare<M extends Model<M>, T> {

    Junction<M> equal(T value);

    Junction<M> notEqual(T value);

    Junction<M> like(T value);

    Junction<M> notLike(T value);

    Junction<M> in(T... values);

    Junction<M> notIn(T... values);

    Junction<M> isNull();

    Junction<M> isNotNull();

    Junction<M> greaterThan(T value);

    Junction<M> greaterThanOrEqual(T value);

    Junction<M> lessThan(T value);

    Junction<M> lessThanOrEqual(T value);

}
