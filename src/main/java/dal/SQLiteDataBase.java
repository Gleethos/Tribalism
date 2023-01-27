package dal;

import dal.api.*;
import swingtree.api.mvvm.*;

import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *  This class constitutes both a representation of a database
 *  and define an API which is in essence an interface based ORM.
 */
public class SQLiteDataBase extends AbstractDataBase {
    private final ModelRegistry _modelRegistry = new ModelRegistry();

    public SQLiteDataBase(String location) {
        super(location, "", "");
    }

    SQLiteDataBase() {
        super("jdbc:sqlite:"+new File("saves/dbs").getAbsolutePath(), "", "");
    }

    @Override
    public void execute(String sql) {
        _execute(sql);
    }


    @Override
    public void dropTablesFor(
            Class<? extends Model<?>>... models
    ) {
        for (Class<? extends Model<?>> model : models)
            _dropTableIfExists(model);
    }

    @Override
    public void dropAllTables() {
        _dropAllTables();
    }

    private void _createTableIfNotExists( Class<? extends Model<?>> model ) {
        if ( !doesTableExist(_tableNameFromClass(model)) )
            _modelRegistry.addTables(Collections.singletonList((Class<? extends Model<?>>) model));
    }

    private void _dropTableIfExists(Class<? extends Model<?>> model) {
        if (doesTableExist(_tableNameFromClass(model)))
            dropTable(model);
    }

    @Override
    public void dropTable(Class<? extends Model<?>> model) {
        String tableName = _tableNameFromClass(model);
        _execute("DROP TABLE IF EXISTS " + tableName);

    }

    private void _dropAllTables() {
        List<String> tableNames = this.listOfAllTableNames();
        for ( String tableName : tableNames ) {
            _execute("DROP TABLE IF EXISTS " + tableName);
        }
    }

    @Override
    public void createTablesFor(
            Class<? extends Model<?>>... models
    ) {
        _modelRegistry.addTables(Arrays.asList(models));
        for ( String statement : _modelRegistry.getCreateTableStatements() ) {
            _execute(statement);
        }
    }

    /**
     *  This reads the sql defining the table of the provided model type.
     *
     * @param model The model type
     * @return The sql defining the table of the provided model type
     */
    @Override
    public String sqlCodeOfTable(Class<? extends Model<?>> model) {
        // We query the database for the sql code of the table
        var sql = new StringBuilder();
        sql.append("SELECT sql FROM sqlite_master WHERE type='table' AND name='");
        sql.append(_tableNameFromClass(model));
        sql.append("'");
        Map<String, List<Object>> result = _query(sql.toString());
        if ( result.isEmpty() )
            throw new IllegalArgumentException("The model '" + model.getName() + "' does not have a table in the database!");
        if ( result.size() > 1 )
            throw new IllegalArgumentException("There are multiple tables for the model '" + model.getName() + "' in the database!");
        return (String) result.get("sql").get(0);
    }

    @Override
    public <T extends Model<T>> T select(Class<T> model, int id) {
        // First let's verify that the model is indeed a model
        if ( !Model.class.isAssignableFrom(model) )
            throw new IllegalArgumentException("The provided class is not a model!");

        // Now let's verify that the table exists
        if ( !doesTableExist(_tableNameFromClass(model)) )
            throw new IllegalArgumentException("The table for the model '" + model.getName() + "' does not exist!");

        // Now let's verify that the id is valid
        if ( id < 0 )
            throw new IllegalArgumentException("The id must be a positive integer!");

        /*
            Now you might thing we simply do a single database query to get the model
            and then that'ss it. But that is not the case.
            This ORM is interface based, so we are free to implement the model in any way we want.
            And what we want is dynamic models where calling the setter of a property updates
            the database.
            To achieve this we need to create a proxy object that will do that.
            Let's do that now:
        */
        // Let's find the table for the model
        ModelTable modelTable = _modelRegistry.getTable(model);

        return  (T) Proxy.newProxyInstance(
                        model.getClassLoader(),
                        new Class[]{model},
                        new ModelProxy<>(this, modelTable, id)
                );
    }

    @Override
    public <M extends Model<M>> List<M> selectAll(Class<M> models) {
        // First we need to query the database for all the ids of the models
        String tableName = _tableNameFromClass(models);
        String sql = "SELECT id FROM " + tableName;
        Map<String, List<Object>> result = _query(sql);
        if ( result.isEmpty() )
            throw new IllegalArgumentException("The model '" + models.getName() + "' does not have a table in the database!");
        if ( result.size() > 1 )
            throw new IllegalArgumentException("There are multiple tables for the model '" + models.getName() + "' in the database!");
        List<Object> ids = result.get("id");

        List<M> modelsList = new ArrayList<>();
        for ( Object id : ids ) {
            modelsList.add(select(models, (int) id));
        }

        return modelsList;
    }

    @Override
    public <M extends Model<M>> M create(Class<M> model) {
        // First let's verify that the model is indeed a model
        if ( !Model.class.isAssignableFrom(model) )
            throw new IllegalArgumentException("The provided class is not a model!");

        // Now let's verify that the table exists
        if ( !doesTableExist(_tableNameFromClass(model)) )
            throw new IllegalArgumentException("The table for the model '" + model.getName() + "' does not exist!");

        // Now let's create the model
        ModelTable modelTable = _modelRegistry.getTable(model);
        List<TableField> fields = modelTable.getFields();
        List<Object> defaultValues = modelTable.getDefaultValues();
        List<String> fieldNames    = fields.stream().map(TableField::getName).collect(Collectors.toList());
        /*
            Now there might be a problem here because some model fields might not actually exist
            in the table explicitly. Namely, if the model references multiple other models
            through a Vars or Vals field!
            So we need to check for that and remove those fields from the list of fields
        */
        for ( int i = fields.size()-1; i >= 0; i-- ) {
            TableField field = fields.get(i);
            if ( field.getKind() == FieldKind.INTERMEDIATE_TABLE ) {
                fieldNames.remove(i);
                defaultValues.remove(i);
            }
        }

        int idIndex = -1;
        for ( int i = 0; i < fieldNames.size(); i++ ) {
            if ( fieldNames.get(i).equals("id") ) {
                idIndex = i;
                break;
            }
        }
        if ( idIndex == -1 )
            throw new IllegalArgumentException("The model '" + model.getName() + "' does not have an id field!");
        else {
            defaultValues.remove(idIndex);
            fieldNames.remove(idIndex);
        }
        String tableName = _tableNameFromClass(model);
        String sql =
                "INSERT INTO " + tableName +
                " (" + String.join(", ", fieldNames) + ") " +
                " VALUES (" + IntStream.range(0, fieldNames.size()).mapToObj(i -> " ? ").collect(Collectors.joining(",")) + ")";
        boolean success = _update(sql, defaultValues);
        if ( !success )
            throw new IllegalArgumentException(
                    "Failed to create create a database entry for model '" + model.getName() + "' " +
                    "using SQL code '" + sql + "' and default values [" +
                        defaultValues.stream().map( o -> {
                            if ( o == null )
                                return "null";
                            else if ( o instanceof String )
                                return "\"" + o + "\"";
                            else
                                return o.toString();
                        }).collect(Collectors.joining(", "))
                    + "]!"
            );

        // Now let's get the id of the model
        sql = "SELECT last_insert_rowid()";
        Map<String, List<Object>> result = _query(sql);
        if ( result.isEmpty() )
            throw new IllegalArgumentException("The model '" + model.getName() + "' does not have a table in the database!");
        if ( result.size() > 1 )
            throw new IllegalArgumentException("There are multiple tables for the model '" + model.getName() + "' in the database!");
        int id = (int) result.get("last_insert_rowid()").get(0);

        return select(model, id);
    }

    @Override
    public <M extends Model<M>> void delete(M model) {
        Objects.requireNonNull(model, "The provided model is null!");
        Class<?> modelProxyClass = model.getClass();
        // Now we need to get the interface class defining the model
        Class<?> modelInterfaceClass = null;
        for ( Class<?> c : modelProxyClass.getInterfaces() ) {
            if ( Model.class.isAssignableFrom(c) ) {
                modelInterfaceClass = c;
                break;
            }
        }
        int id = model.id().get();
        String tableName = _tableNameFromClass(modelInterfaceClass);
        String sql = "DELETE FROM " + tableName + " WHERE id = ?";
        boolean success = _update(sql, Collections.singletonList(id));
    }

    @Override
    public <M extends Model<M>> Where<M> select(Class<M> model) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(_tableNameFromClass(model)).append(" WHERE ");
        ModelTable table = _modelRegistry.getTable(model);
        List<Object> values = new ArrayList<>();
        Junction[] junc = {null};
        Compare<M, Object> valueCollector = new Compare<M, Object>() {
            @Override
            public Junction<M> equal(Object value) {
                // First sql:
                sql.append(" = ?");
                // Then values:
                values.add(value);
                return junc[0];
            }

            @Override
            public Junction<M> notEqual(Object value) {
                // First sql:
                sql.append(" != ?");
                // Then values:
                values.add(value);
                return junc[0];
            }

            @Override
            public Junction<M> like(Object value) {
                // First sql:
                sql.append(" LIKE ?");
                // Then values:
                values.add(value);
                return junc[0];
            }

            @Override
            public Junction<M> notLike(Object value) {
                // First sql:
                sql.append(" NOT LIKE ?");
                // Then values:
                values.add(value);
                return junc[0];
            }

            @Override
            public Junction<M> in(Object... objects) {
                // First sql:
                sql.append(" IN (");
                for ( int i = 0; i < objects.length; i++ ) {
                    sql.append("?");
                    if ( i < objects.length-1 )
                        sql.append(", ");
                }
                sql.append(")");
                // Then values:
                values.addAll(Arrays.asList(objects));
                return junc[0];
            }

            @Override
            public Junction<M> notIn(Object... objects) {
                // First sql:
                sql.append(" NOT IN (");
                for ( int i = 0; i < objects.length; i++ ) {
                    sql.append("?");
                    if ( i < objects.length-1 )
                        sql.append(", ");
                }
                sql.append(")");
                // Then values:
                values.addAll(Arrays.asList(objects));
                return junc[0];
            }

            @Override
            public Junction<M> isNull() {
                // First sql:
                sql.append(" IS NULL");
                // Then values:
                return junc[0];
            }

            @Override
            public Junction<M> isNotNull() {
                // First sql:
                sql.append(" IS NOT NULL");
                // Then values:
                return junc[0];
            }

            @Override
            public Junction<M> greaterThan(Object value) {
                // First sql:
                sql.append(" > ?");
                // Then values:
                values.add(value);
                return junc[0];
            }

            @Override
            public Junction<M> greaterThanOrEqual(Object value) {
                // First sql:
                sql.append(" >= ?");
                // Then values:
                values.add(value);
                return junc[0];
            }

            @Override
            public Junction<M> lessThan(Object value) {
                // First sql:
                sql.append(" < ?");
                // Then values:
                values.add(value);
                return junc[0];
            }

            @Override
            public Junction<M> lessThanOrEqual(Object value) {
                // First sql:
                sql.append(" <= ?");
                // Then values:
                values.add(value);
                return junc[0];
            }

        };

        junc[0] = new Junction<M>() {

            @Override
            public <T> Compare<M, T> and(Class<? extends Val<T>> field) {
                sql.append(" AND ");
                sql.append(table.getField(field).getName());
                return (Compare<M, T>) valueCollector;
            }

            @Override
            public <T> Compare<M, T> or(Class<? extends Val<T>> field) {
                sql.append(" OR ");
                sql.append(table.getField(field).getName());
                return (Compare<M, T>) valueCollector;
            }

            @Override
            public Query<M> orderAscendingBy(Class<? extends Val<?>> field) {
                sql.append(" ORDER BY ");
                sql.append(table.getField(field).getName());
                sql.append(" ASC");
                return this;
            }

            @Override
            public Query<M> orderDescendingBy(Class<? extends Val<?>> field) {
                sql.append(" ORDER BY ");
                sql.append(table.getField(field).getName());
                sql.append(" DESC");
                return this;
            }

            @Override
            public List<M> asList() {
                Map<String, List<Object>> result = _query(sql.toString(), values);
                List<Integer> ids = result.getOrDefault("id", Collections.emptyList())
                                            .stream()
                                            .map( o -> (int) o )
                                            .toList();

                // Now let's select them:
                return ids.stream()
                            .map( id -> select(model, id) )
                            .toList();
            }
        };

        return new Where<M>() {
            @Override
            public <T> Compare<M, T> where(Class<? extends Val<T>> field) {
                // First sql:
                sql.append(table.getField(field).getName()).append(" ");
                // Then values:
                return (Compare<M, T>) valueCollector;
            }

            @Override
            public List<M> asList() {
                return junc[0].asList();
            }
        };
    }

}
