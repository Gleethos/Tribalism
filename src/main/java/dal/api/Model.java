package dal.api;

import swingtree.api.mvvm.Val;

import java.util.function.Consumer;

public interface Model<M>
{
    Id id();

    void commit( Consumer<M> transaction );

    M clone();


    interface Id extends Val<Integer> {
        default boolean greaterThan(int value) {
            Integer id = orElseNull();
            return id != null && id > value;
        }
        default boolean greaterThanOrEqual(int value) {
            Integer id = orElseNull();
            return id != null && id >= value;
        }
        default boolean lessThan(int value) {
            Integer id = orElseNull();
            return id != null && id < value;
        }
        default boolean lessThanOrEqual(int value) {
            Integer id = orElseNull();
            return id != null && id <= value;
        }
        default boolean between(int min, int max) {
            Integer id = orElseNull();
            return id != null && id >= min && id <= max;
        }
        default boolean notBetween(int min, int max) {
            Integer id = orElseNull();
            return id != null && (id < min || id > max);
        }
    }

}
