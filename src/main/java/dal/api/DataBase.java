package dal.api;

import dal.impl.SQLiteDataBase;

import java.util.List;
import java.util.Objects;

/**
 *  This is the most important interface of the Topsoil ORM API which defines
 *  the database connection in terms of a thing that can create, delete and query
 *  extensions of the {@link Model} interface.
 *  The {@link DataBase} will implement your model interfaces for you through proxies
 *  which will eagerly translate the interactions with your model objects
 *  into SQL queries and updates. All the complicated SQL code is generated
 *  at run time by the internal {@link SQLiteDataBase} class,
 *  you don't have to think about it.
 *  This type of ORM is especially useful for Swing applications or other
 *  desktop applications where many small database operations are not a problem. <br>
 *  You simply create a {@link DataBase} instance by calling the static method like so:
 *  <pre>{@code
 *      DataBase db = DataBase.at("path/to/my/my.db");
 *  }</pre>
 *  The {@link DataBase} will then create a connection to the underlying SQLite database
 *  and will create a database file if it does not exist already, this is where all the
 *  data will be stored. <br>
 *  Before creating model instances you have to register them with the {@link DataBase}
 *  so that it can create the tables for them.
 *  If your models have relations to other models you have to register the involved model types all
 *  at once by passing them to the {@link DataBase#createTablesFor(Class[])}
 *  method. <br>
 *  You can then create a model by calling the {@link DataBase#create(Class)} method.<br>
 *  Here is an example of how to do this:
 *  <pre>{@code
 *    DataBase db = DataBase.at("path/to/my/my.db");
 *    // Register the model
 *    db.createTablesFor(MyModel.class, MyOtherModel.class);
 *    // Create a model instance
 *    MyModel model = db.create(MyModel.class);
 *    // Do something with the model
 *    System.out.println(model.id().get());
 *    model.someProperty().set("Hello World!");
 *    // The model will be persisted automatically!
 *  } </pre>
 *  You can also query the database by using the {@link DataBase#select(Class)} method
 *  like so: <br>
 *  <pre>{@code
 *    DataBase db = DataBase.at("path/to/my/my.db");
 *    // ... some code to register and create many models ...
 *    // Query the database:
 *    List<MyModel> models = db.select(MyModel.class)
 *                                .where(MyModel::someProperty).is("Hello World!")
 *                                .asList();
 * }</pre>
 */
public interface DataBase
{
    /**
     * Creates a new {@link DataBase} instance representing a database at
     * the specified path.
     * If a database already exists at the specified path it will be opened,
     * otherwise a new database file will be created.
     *
     * @param path The path to the database file.
     * @return A new {@link DataBase} instance.
     */
    static DataBase at( String path ) {
        Objects.requireNonNull(path);
        return new SQLiteDataBase(path);
    }

    void createTablesFor( Class<? extends Model<?>>... models );

    void dropTablesFor( Class<? extends Model<?>>... models );

    List<String> listOfAllTableNames();

    void execute(String sql);

    void dropAllTables();

    void dropTable( Class<? extends Model<?>> model );

    String sqlCodeOfTable( Class<? extends Model<?>> model );

    <M extends Model<M>> M create( Class<M> model );

    <T extends Model<T>> T select( Class<T> model, int id );

    <M extends Model<M>> List<M> selectAll( Class<M> models );

    <M extends Model<M>> void delete( M model );

    default <M extends Model<M>> void delete( List<M> models ) {
        models.forEach(this::delete);
    }

    default <M extends Model<M>> void delete( Query<M> modelQuery ) {
        modelQuery.asList().forEach(this::delete);
    }

    /**
     *  Exposes a fluent builder API in the form of the {@link Query} interface.
     *  Use this to query the database similar to SQL.
     *  Here is an example:
     *  <pre>{@code
     *    List<User> users = db.select(User.class)
     *                          .where(User::name).is("John")
     *                          .and(User::age).greaterThan(18)
     *                          .asList();
     * }</pre>
     * @param model The model type class used to find the table in the database.
     * @return A {@link Query} object which exposes a fluent builder API.
     * @param <M> The type of the model to query.
     */
    <M extends Model<M>> Where<M> select( Class<M> model );

    /**
     *  Closes the database connection.
     */
    void close();

}
