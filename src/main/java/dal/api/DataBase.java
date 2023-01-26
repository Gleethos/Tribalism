package dal.api;

import dal.SQLiteDataBase;

import java.util.List;
import java.util.Objects;

public interface DataBase {

    static DataBase of(String path) {
        Objects.requireNonNull(path);
        return new SQLiteDataBase(path);
    }

    List<String> listOfAllTableNames();

    void execute(String sql);

    void dropTablesFor( Class<? extends Model<?>>... models );

    void dropAllTables();

    void dropTable( Class<? extends Model<?>> model );

    void createTablesFor( Class<? extends Model<?>>... models );

    String sqlCodeOfTable(Class<? extends Model<?>> model);

    <T extends Model<T>> T select(Class<T> model, int id);

    <M extends Model<M>> List<M> selectAll(Class<M> models);

    <M extends Model<M>> M create(Class<M> model);

    <M extends Model<M>> void remove(M model);

    <M extends Model<M>> Where<M> select(Class<M> model);

}
