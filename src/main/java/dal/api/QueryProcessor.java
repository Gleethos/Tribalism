package dal.api;

import java.util.function.Supplier;

public interface QueryProcessor
{

    void process(Runnable task);

    void processNow(Runnable task);

    default <T> T processNowAndGet( Supplier<T> task ) {
        T[] result = (T[]) new Object[1];
        processNow(() -> result[0] = task.get());
        return result[0];
    }
}
