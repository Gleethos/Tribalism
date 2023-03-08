package dal.impl;

import dal.api.*;
import org.slf4j.Logger;
import sprouts.Val;
import sprouts.Vars;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static dal.impl.ModelTable.INTER_TABLE_POSTFIX;

/**
 *  This class constitutes both a representation of a database
 *  and define an API which is in essence an interface based ORM.
 */
public class SQLiteDataBase extends AbstractDataBase
{
    private final static Logger log = org.slf4j.LoggerFactory.getLogger(SQLiteDataBase.class);

    private final ModelRegistry _modelRegistry = new ModelRegistry();

    public SQLiteDataBase(String location, DataBaseProcessor processor) {
        super(location, "", "", processor);
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
        for ( String statement : getCreateTableStatements() ) {
            _execute(statement);
        }
    }


    private List<String> getCreateTableStatements() {
        List<String> allExistingTables = listOfAllTableNames();
        List<String> statements = new ArrayList<>();
        for ( ModelTable modelTable : _modelRegistry.getTables() ) {
            if ( !allExistingTables.contains(modelTable.getTableName()) )
                statements.add(modelTable.createTableStatement());
            else {
                log.info("Table " + modelTable.getTableName() + " already exists!");
                var collision = modelTable.getTableName();
                var statement = modelTable.createTableStatement();
                /*
                    Before we return the statements we need to carefully look at the table name collisions.
                    For every single table name collision we need to check if the sql code of the table
                    is the same as the sql code of the table we are trying to create!
                    If it is not the same we need to throw an exception because
                    it means that the database is in an inconsistent state with the model (source code).
                */
                var tableSQL = sqlCodeOfTable(modelTable.getTableName());
                // Before checking for equality we strip both strings of all executive whitespace and
                // other characters that are not part of the sql code like newlines and tabs.
                tableSQL = tableSQL.replaceAll("\\s+", " ").trim();
                statement = statement.replaceAll("\\s+", " ").trim();
                // Next we remove SQL syntax that is not part of the table definition
                tableSQL = tableSQL.replace("CREATE TABLE IF NOT EXISTS ", "CREATE TABLE ");
                statement = statement.replace("CREATE TABLE IF NOT EXISTS ", "CREATE TABLE ");
                // We trim semicolons from the end of the sql code
                if ( tableSQL.endsWith(";") )
                    tableSQL = tableSQL.substring(0, tableSQL.length()-1);
                if ( statement.endsWith(";") )
                    statement = statement.substring(0, statement.length()-1);
                // We check for equality
                if ( !tableSQL.equals(statement) ) {
                    throw new IllegalStateException(
                            "The database at '" + getURL() + "' is not compatible with the provided source code model" +
                            modelTable.getModelInterface().map( m -> " '" + m.getName() + "'" ).orElse("") + "! \n" +
                            "The sql code of table '" + collision + "' encountered inside the database, \n" +
                            "does not match the table statement generated from " +
                            "the model source code. \nThis means that the database is not compatible with the source code " +
                            "of the model" + modelTable.getModelInterface().map( m -> " '" + m.getName() + "'" ).orElse("") +
                            ". \nThe sql code of the table is: \n'" + tableSQL + "', \nwhereas the table " +
                            "statement necessary for representing the current model interface is: \n'" + statement + "'."
                        );
                }
            }
        }
        return statements;
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

    public String sqlCodeOfTable(String tableName) {
        // We query the database for the sql code of the table
        var sql = new StringBuilder();
        sql.append("SELECT sql FROM sqlite_master WHERE type='table' AND name='");
        sql.append(tableName);
        sql.append("'");
        Map<String, List<Object>> result = _query(sql.toString());
        if ( result.isEmpty() )
            throw new IllegalArgumentException("The table '" + tableName + "' does not exist in the database!");
        if ( result.size() > 1 )
            throw new IllegalArgumentException("There are multiple tables for the name '" + tableName + "' in the database!");
        return (String) result.get("sql").get(0);
    }

    private ModelTable _getTableFor( Class<? extends Model<?>> model ) {
        // First let's verify that the model is indeed a model
        if ( !Model.class.isAssignableFrom(model) )
            throw new IllegalArgumentException("The provided class is not a model!");

        // Now let's verify that the table exists
        if ( !doesTableExist(_tableNameFromClass(model)) )
            throw new IllegalArgumentException("The table for the model '" + model.getName() + "' does not exist!");

        return _modelRegistry.getTable(model)
                            .orElseThrow(()->new RuntimeException(
                                "The model '" + model.getName() + "' does have a " +
                                "table in the database, but the model type is not known " +
                                "to the TopSoil ORM!\n " +
                                "This is most likely because the model was not registered " +
                                "through the 'createTablesFor(Class)' method of the TopSoil DataBase API."
                            ));
    }

    @Override
    public <T extends Model<T>> T select( Class<T> model, int id )
    {
        // Now let's verify that the id is valid
        if ( id < 0 )
            throw new IllegalArgumentException("The id must be a positive integer!");
        /*
            Now you might think we simply do a single database query to get the model
            and then that's it. But that is not the case.
            This ORM is interface based, so we are free to implement the model in any way we want.
            And what we want is dynamic models where calling the setter of a property updates
            the database.
            To achieve this we need to create a proxy object that will do that.
            Let's do that now:
        */
        // Let's find the table for the model
        var modelTable = _getTableFor(model);

        // Let's first see if the registry already contains a proxy
        var proxy = _modelRegistry.findModelProxy(_tableNameFromClass(model), id).orElse(null);
        if ( proxy == null ) {
            proxy = new ModelProxy<>(this, modelTable, id, true);
            _modelRegistry.addModelProxy(proxy);
        }
        return  (T) Proxy.newProxyInstance(
                        model.getClassLoader(),
                        new Class[]{model},
                        proxy
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
        for ( Object id : ids )
            modelsList.add(select(models, (int) id));

        return modelsList;
    }

    @Override
    public <M extends Model<M>> M create( Class<M> model )
    {
        // First let's verify that the model is indeed a model
        if ( !Model.class.isAssignableFrom(model) )
            throw new IllegalArgumentException("The provided class is not a model!");

        // Now let's verify that the table exists
        if ( !doesTableExist(_tableNameFromClass(model)) )
            throw new IllegalArgumentException("The table for the model '" + model.getName() + "' does not exist!");

        // Now let's create the model
        ModelTable modelTable      = _getTableFor(model);
        List<TableField> fields    = modelTable.getFields();
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
        for ( int i = 0; i < fieldNames.size(); i++ )
            if ( fieldNames.get(i).equals("id") ) {
                idIndex = i;
                break;
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
                "VALUES (" + IntStream.range(0, fieldNames.size()).mapToObj(i -> " ? ").collect(Collectors.joining(",")) + ")";
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
    public <M extends Model<M>> void delete( M modelToBeRemoved ) {
        Objects.requireNonNull(modelToBeRemoved, "The provided model is null!");
        Class<?> modelProxyClass = modelToBeRemoved.getClass();
        // Now we need to get the interface class defining the model
        Class<?> modelInterfaceClass =
                Arrays.stream(modelProxyClass.getInterfaces())
                        .filter(Model.class::isAssignableFrom)
                        .findFirst()
                        .orElseThrow();

        int id = modelToBeRemoved.id().get();
        String tableName = _tableNameFromClass(modelInterfaceClass);
        // First we clean up usages of the model
        // Now we need to find all the intermediate tables that reference this model
        List<ModelTable> intermediateTables = _modelRegistry.getIntermediateTableInvolving((Class<? extends Model<?>>) modelInterfaceClass);
        intermediateTables.forEach( intermTable -> {
            String intermTableName = intermTable.getTableName();
            Class<?> left = intermTable.getReferencedModels().get(0);
            Class<?> right = intermTable.getReferencedModels().get(1);
            String leftName = ModelTable.INTER_LEFT_FK_PREFIX + _tableNameFromClass(left) + ModelTable.INTER_FK_POSTFIX;
            String rightName = ModelTable.INTER_RIGHT_FK_PREFIX + _tableNameFromClass(right) + ModelTable.INTER_FK_POSTFIX;
            // We need to find all entries where 'fk_..._id' is this 'id'
            // Then we need to find all the referencing (containing "self") models and simply
            // call the right property using reflection and tell it to remove the model...
            var result = _query(
                        "SELECT " + leftName + " " +
                                "FROM " + intermTableName + " " +
                                "WHERE " + rightName + " = ?",
                                List.of(id)
                            );

            if ( result.isEmpty() ) return;
            List<Integer> refIds = result.get(leftName).stream().map( o -> (Integer) o ).distinct().toList();
            refIds.forEach( refId -> {
                var refModel = select((Class<Model>) left, refId);
                String prefix = _nameFromClass(left) + "__";
                String methodName = intermTableName.substring(0, intermTableName.length() - INTER_TABLE_POSTFIX.length());
                methodName = methodName.substring(prefix.length());
                Vars<Object> listOfModels = null;
                // Let's call the method:
                try {
                    Method m = refModel.getClass().getMethod(methodName);
                    listOfModels = (Vars<Object>) m.invoke(refModel);
                    listOfModels.remove(modelToBeRemoved);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            });
        });
        _modelRegistry.findModelProxy(tableName, id).ifPresent( proxy -> {
            _modelRegistry.removeModelProxy(tableName, id);
        });
        String sql = "DELETE FROM " + tableName + " WHERE id = ?";
        boolean success = _update(sql, Collections.singletonList(id));
    }

    @Override
    public <M extends Model<M>> Where<M> select(Class<M> model) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(_tableNameFromClass(model)).append(" WHERE ");
        ModelTable table = _getTableFor(model);
        List<Object> values = new ArrayList<>();
        Junction[] junc = {null};
        Compare<M, Object> valueCollector = new Compare<>() {
            @Override
            public Junction<M> is(Object value) {
                // First sql:
                sql.append(" = ?");
                // Then values:
                values.add(value);
                return junc[0];
            }

            @Override
            public Junction<M> isNot(Object value) {
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
                for (int i = 0; i < objects.length; i++) {
                    sql.append("?");
                    if (i < objects.length - 1)
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
                for (int i = 0; i < objects.length; i++) {
                    sql.append("?");
                    if (i < objects.length - 1)
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
            public <T> Compare<M, T> and(Function<M, Val<T>> selector) {
                var field = _selectTableField(selector, model);
                sql.append(" AND ").append(field.getName()).append(" ");
                return (Compare<M, T>) valueCollector;
            }

            @Override
            public <T> Compare<M, T> or( Function<M, Val<T>> selector ) {
                var field = _selectTableField(selector, model);
                sql.append(" OR ").append(field.getName()).append(" ");
                return (Compare<M, T>) valueCollector;
            }

            @Override
            public <T> Compare<M, T> and( Class<? extends Val<T>> field ) {
                sql.append(" AND ").append(table.getField(field).getName());
                return (Compare<M, T>) valueCollector;
            }

            @Override
            public <T> Compare<M, T> or( Class<? extends Val<T>> field ) {
                sql.append(" OR ");
                sql.append(table.getField(field).getName());
                return (Compare<M, T>) valueCollector;
            }

            @Override
            public <N extends Number> Query<M> orderAscendingBy( Function<M, Val<N>> selector ) {
                var field = _selectTableField(selector, model);
                sql.append(" ORDER BY ");
                sql.append(field.getName());
                sql.append(" ASC");
                return this;
            }

            @Override
            public <N extends Number> Query<M> orderDescendingBy( Function<M, Val<N>> selector ) {
                var field = _selectTableField(selector, model);
                sql.append(" ORDER BY ");
                sql.append(field.getName());
                sql.append(" DESC");
                return this;
            }

            @Override
            public Query<M> orderAscendingBy( Class<? extends Val<?>> field ) {
                sql.append(" ORDER BY ");
                sql.append(table.getField(field).getName());
                sql.append(" ASC");
                return this;
            }

            @Override
            public Query<M> orderDescendingBy( Class<? extends Val<?>> field ) {
                sql.append(" ORDER BY ");
                sql.append(table.getField(field).getName());
                sql.append(" DESC");
                return this;
            }

            @Override
            public List<M> asList() {
                String sqlString = sql.toString();
                if ( sqlString.endsWith(" WHERE ") )
                    sqlString = sqlString.substring(0, sqlString.length()-7);

                Map<String, List<Object>> result = _query(sqlString, values);
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

        return new Where<M>()
        {
            @Override public List<M> asList() { return junc[0].asList(); }

            @Override
            public <T> Compare<M, T> where( Class<? extends Val<T>> field ) {
                // First sql:
                sql.append(table.getField(field).getName()).append(" ");
                // Then values:
                return (Compare<M, T>) valueCollector;
            }

            @Override
            public <T> Compare<M, T> where( Function<M, Val<T>> selector )
            {
                var field = _selectTableField(selector, model);
                // First sql:
                sql.append(field.getName()).append(" ");
                // Then values:
                return (Compare<M, T>) valueCollector;
            }
        };
    }

    private <T, M extends Model<M>> TableField _selectTableField(
        Function<M, Val<T>> selector,
        Class<M> model
    ) {
        var propSelector = new PropertySelectionProxy(_getTableFor(model));
        selector.apply((M) Proxy.newProxyInstance(
                                model.getClassLoader(),
                                new Class<?>[]{model},
                                propSelector
                            ));

        return propSelector.getSelection().orElseThrow();
    }

    public Map<String, List<String>> query(String sql) {
        Map<String, List<Object>> result = _query(sql, Collections.emptyList());
        return result.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(Object::toString)
                                .toList()
                    ));
    }

}
