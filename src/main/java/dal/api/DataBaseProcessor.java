package dal.api;

import java.util.List;
import java.util.function.Supplier;

/**
 *  Implementations of this are used by a particular {@link DataBase} instance to process
 *  database operations.
 *  The database thread is expected to be the same thread as the one used to create the database
 *  through the {@link DataBase#at(String, DataBaseProcessor)} method.
 *  <p>
 *  Use this to hook your database into the application's event loop. If your frontend thread
 *  dares to access the database in some way, the database will use this processor to process
 *  the database operations on the database thread through the {@link #process(Runnable)} method
 *  and the {@link #processNow(Runnable)} method.
 *  <p>
 *      <b>Important:</b> The {@link #processNow(Runnable)} method is expected to block until the
 *      operation is completed. This is used by the {@link #processNowAndGet(Supplier)} method.
 *      The {@link #process(Runnable)} method on the other hand is expected to return immediately.
 *  <p>
 *  Implementations of this are expected to be used in the creation of a {@link DataBase} instance:
 *  <pre>{@code
 *      DataBase db = DataBase.at("path/to/saved.db", new MyDataBaseProcessor());
 *  }</pre>
 */
public interface DataBaseProcessor
{
    /**
     *  Passes the given task to the database thread (usually the application thread) to be processed.
     *  This method is expected to return immediately.
     *  @param task The task to process.
     */
    void process(Runnable task);

    /**
     *  Passes the given task to the database thread (usually the application thread) to be processed, and
     *  blocks until the task is completed.
     *  This means that when this method returns, the task has to be completed.
     *
     *  @param task The task to process now.
     */
    void processNow(Runnable task);

    /**
     *  Passes the given task to the database thread (usually the application thread) to be processed, and
     *  blocks until the task is completed. The result of the task is returned.
     *
     *  @param task The task to process now and get the result of.
     *  @return The result of the task that was processed on the database thread (usually the application thread).
     */
    default <T> T processNowAndGet( Supplier<T> task ) {
        T[] result = (T[]) new Object[1];
        processNow(() -> result[0] = task.get());
        return result[0];
    }

    /**
     *  @return A list of all threads that are allowed to access the database.
     */
    List<Thread> getThreads();
}
