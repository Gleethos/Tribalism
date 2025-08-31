package dal.impl;

import dal.api.*;
import org.slf4j.Logger;
import sprouts.Tuple;
import sprouts.Val;
import sprouts.Vars;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static dal.impl.EntityTable.INTER_TABLE_POSTFIX;

/**
 *  This class constitutes both a representation of a database
 *  and define an API which is in essence an interface based ORM.
 */
public final class SQLiteDataBase implements DataBase
{
    private final static Logger log = org.slf4j.LoggerFactory.getLogger(SQLiteDataBase.class);

    final BasicSQLiteDataBase _db;
    private final EntityRegistry _entityRegistry = new EntityRegistry();

    public SQLiteDataBase(String location, DataBaseProcessor processor) {
        _db = new BasicSQLiteDataBase(location, "", "", processor);
    }

    @Override
    public void execute(String sql) {
        _db._execute(sql);
    }


    @Override
    public void dropTablesFor(
            Class<? extends DataBaseEntity>... models
    ) {
        for (Class<? extends DataBaseEntity> model : models)
            _dropTableIfExists(model);
    }

    @Override
    public List<String> listOfAllTableNames() {
        return _db.listOfAllTableNames();
    }

    @Override
    public void dropAllTables() {
        _dropAllTables();
    }

    private void _dropTableIfExists(Class<? extends DataBaseEntity> model) {
        if (_db.doesTableExist(BasicSQLiteDataBase._tableNameFromClass(model)))
            dropTable(model);
    }

    @Override
    public void dropTable(Class<? extends DataBaseEntity> model) {
        String tableName = BasicSQLiteDataBase._tableNameFromClass(model);
        _db._execute("DROP TABLE IF EXISTS " + tableName);

    }

    private void _dropAllTables() {
        List<String> tableNames = this.listOfAllTableNames();
        for ( String tableName : tableNames ) {
            _db._execute("DROP TABLE IF EXISTS " + tableName);
        }
    }

    @Override
    public void createTablesFor(
            Class<? extends DataBaseEntity>... models
    ) {
        _entityRegistry.addTables(Arrays.asList(models));
        for ( String statement : getCreateTableStatements() ) {
            _db._execute(statement);
        }
    }


    private List<String> getCreateTableStatements() {
        List<String> allExistingTables = listOfAllTableNames();
        List<String> statements = new ArrayList<>();
        for ( EntityTable modelTable : _entityRegistry.getTables() ) {
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
                            "The database at '" + _db.getURL() + "' is not compatible with the provided source code model" +
                            modelTable.entityType().map(m -> " '" + m.getName() + "'" ).orElse("") + "! \n" +
                            "The sql code of table '" + collision + "' encountered inside the database, \n" +
                            "does not match the table statement generated from " +
                            "the model source code. \nThis means that the database is not compatible with the source code " +
                            "of the model" + modelTable.entityType().map(m -> " '" + m.getName() + "'" ).orElse("") +
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
    public String sqlCodeOfTable(Class<? extends DataBaseEntity> model) {
        // We query the database for the sql code of the table
        var sql = new StringBuilder();
        sql.append("SELECT sql FROM sqlite_master WHERE type='table' AND name='");
        sql.append(BasicSQLiteDataBase._tableNameFromClass(model));
        sql.append("'");
        Map<String, List<Object>> result = _db._query(sql.toString());
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
        Map<String, List<Object>> result = _db._query(sql.toString());
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
        if ( !_db.doesTableExist(BasicSQLiteDataBase._tableNameFromClass(model)) )
            throw new IllegalArgumentException("The table for the model '" + model.getName() + "' does not exist!");

        return _entityRegistry.getTable(model)
                            .orElseThrow(()->new RuntimeException(
                                "The model '" + model.getName() + "' does have a " +
                                "table in the database, but the model type is not known " +
                                "to the TopSoil ORM!\n " +
                                "This is most likely because the model was not registered " +
                                "through the 'createTablesFor(Class)' method of the TopSoil DataBase API."
                            ));
    }

    @Override
    public <T extends Model<T>> T select( Class<T> model, long id )
    {
        // Now let's verify that the id is valid
        if ( id <= 0 )
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
        var proxy = _entityRegistry.findModelProxy(BasicSQLiteDataBase._tableNameFromClass(model), id).orElse(null);
        if ( proxy == null ) {
            proxy = new ModelProxy<>(this, modelTable, id, true);
            _entityRegistry.addModelProxy(proxy);
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
        String tableName = BasicSQLiteDataBase._tableNameFromClass(models);
        String sql = "SELECT id FROM " + tableName;
        Map<String, List<Object>> result = _db._query(sql);
        if ( result.isEmpty() )
            throw new IllegalArgumentException("The model '" + models.getName() + "' does not have a table in the database!");
        if ( result.size() > 1 )
            throw new IllegalArgumentException("There are multiple tables for the model '" + models.getName() + "' in the database!");
        List<Object> ids = result.get(EntityTable.ID);

        List<M> modelsList = new ArrayList<>();
        for ( Object id : ids )
            modelsList.add(select(models, (long) id));

        return modelsList;
    }

    @Override
    public <M extends Model<M>> M create( Class<M> model )
    {
        // First let's verify that the model is indeed a model
        if ( !Model.class.isAssignableFrom(model) )
            throw new IllegalArgumentException("The provided class is not a model!");

        // Now let's create the model
        EntityTable modelTable          = _getTableFor(model);
        Tuple<Object> defaultValues = modelTable.getDefaultValues();

        var id = _storeEntity(modelTable, model, defaultValues);

        return select(model, id);
    }

    long _storeEntity(
            EntityTable modelTable,
            Class<? extends DataBaseEntity> model,
            Tuple<Object> defaultValues
    )
    {
        // First let's verify that the model is indeed a model
        if ( !DataBaseEntity.class.isAssignableFrom(model) )
            throw new IllegalArgumentException("The provided class is not a database entity!");

        // Now let's verify that the table exists
        if ( !_db.doesTableExist(BasicSQLiteDataBase._tableNameFromClass(model)) )
            throw new IllegalArgumentException("The table for the model '" + model.getName() + "' does not exist!");

        Tuple<EntityTableField> fields  = modelTable.getFields();
        List<String> fieldNames     = fields.stream().map(EntityTableField::name).collect(Collectors.toList());
        /*
            Now there might be a problem here because some model fields might not actually exist
            in the table explicitly. Namely, if the model references multiple other models
            through a Vars or Vals field!
            So we need to check for that and remove those fields from the list of fields
        */
        boolean hasId = false;
        for ( int i = fields.size()-1; i >= 0; i-- ) {
            EntityTableField field = fields.get(i);
            boolean shouldBeRemoved = false;
            if ( field.getKind() == FieldKind.INTERMEDIATE_TABLE ) {
                shouldBeRemoved = true;
            }
            if ( field.name().equals(EntityTable.ID) ) {
                hasId = true;
                shouldBeRemoved = true;
            }
            if ( shouldBeRemoved ) {
                fieldNames.remove(i);
                defaultValues = defaultValues.removeAt(i);
            }
        }

        if ( !hasId )
            throw new IllegalArgumentException(
                    "The model '" + model.getName() + "' does not have an '"+ EntityTable.ID+"' field. " +
                    "This is most likely a bug in the TopSoil ORM!"
                );

        String tableName = BasicSQLiteDataBase._tableNameFromClass(model);
        String sql =
                "INSERT INTO " + tableName +
                " (" + String.join(", ", fieldNames) + ") " +
                "VALUES (" + IntStream.range(0, fieldNames.size()).mapToObj(i -> " ? ").collect(Collectors.joining(",")) + ")";
        boolean success = _db._update(sql, defaultValues.toList());
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
        Map<String, List<Object>> result = _db._query(sql);
        if ( result.isEmpty() )
            throw new IllegalArgumentException("The model '" + model.getName() + "' does not have a table in the database!");
        if ( result.size() > 1 )
            throw new IllegalArgumentException("There are multiple tables for the model '" + model.getName() + "' in the database!");
        long id = (long) result.get("last_insert_rowid()").get(0);

        return id;
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

        long id = modelToBeRemoved.id().get();
        String tableName = BasicSQLiteDataBase._tableNameFromClass(modelInterfaceClass);
        // First we clean up usages of the model
        // Now we need to find all the intermediate tables that reference this model
        Tuple<IntermediateTable> intermediateTables = _entityRegistry.getIntermediateTableInvolving((Class<? extends Model<?>>) modelInterfaceClass);
        intermediateTables.forEach( intermTable -> {
            String intermTableName = intermTable.getTableName();
            Class<?> left = intermTable.getReferencedModels().get(0);
            Class<?> right = intermTable.getReferencedModels().get(1);
            String leftName = EntityTable.INTER_LEFT_FK_PREFIX + BasicSQLiteDataBase._tableNameFromClass(left) + EntityTable.INTER_FK_POSTFIX;
            String rightName = EntityTable.INTER_RIGHT_FK_PREFIX + BasicSQLiteDataBase._tableNameFromClass(right) + EntityTable.INTER_FK_POSTFIX;
            // We need to find all entries where 'fk_..._id' is this 'id'
            // Then we need to find all the referencing (containing "self") models and simply
            // call the right property using reflection and tell it to remove the model...
            var result = _db._query(
                        "SELECT " + leftName + " " +
                                "FROM " + intermTableName + " " +
                                "WHERE " + rightName + " = ?",
                                List.of(id)
                            );

            if ( result.isEmpty() ) return;
            List<Long> refIds = result.get(leftName).stream().map( o -> (Long) o ).distinct().toList();
            refIds.forEach( refId -> {
                var refModel = select((Class<Model>) left, refId);
                String prefix = BasicSQLiteDataBase._nameFromClass(left) + "__";
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
        _entityRegistry.findModelProxy(tableName, id).ifPresent(proxy -> {
            _entityRegistry.removeModelProxy(tableName, id);
        });
        String sql = "DELETE FROM " + tableName + " WHERE id = ?";
        boolean success = _db._update(sql, Collections.singletonList(id));
    }

    @Override
    public <M extends Model<M>> Where<M> select(Class<M> model) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(BasicSQLiteDataBase._tableNameFromClass(model)).append(" WHERE ");
        EntityTable table = _getTableFor(model);
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
                sql.append(" AND ").append(field.name()).append(" ");
                return (Compare<M, T>) valueCollector;
            }

            @Override
            public <T> Compare<M, T> or( Function<M, Val<T>> selector ) {
                var field = _selectTableField(selector, model);
                sql.append(" OR ").append(field.name()).append(" ");
                return (Compare<M, T>) valueCollector;
            }

            @Override
            public <T> Compare<M, T> and( Class<? extends Val<T>> field ) {
                sql.append(" AND ").append(table.getField(field).name());
                return (Compare<M, T>) valueCollector;
            }

            @Override
            public <T> Compare<M, T> or( Class<? extends Val<T>> field ) {
                sql.append(" OR ");
                sql.append(table.getField(field).name());
                return (Compare<M, T>) valueCollector;
            }

            @Override
            public <N extends Number> Query<M> orderAscendingBy( Function<M, Val<N>> selector ) {
                var field = _selectTableField(selector, model);
                sql.append(" ORDER BY ");
                sql.append(field.name());
                sql.append(" ASC");
                return this;
            }

            @Override
            public <N extends Number> Query<M> orderDescendingBy( Function<M, Val<N>> selector ) {
                var field = _selectTableField(selector, model);
                sql.append(" ORDER BY ");
                sql.append(field.name());
                sql.append(" DESC");
                return this;
            }

            @Override
            public Query<M> orderAscendingBy( Class<? extends Val<?>> field ) {
                sql.append(" ORDER BY ");
                sql.append(table.getField(field).name());
                sql.append(" ASC");
                return this;
            }

            @Override
            public Query<M> orderDescendingBy( Class<? extends Val<?>> field ) {
                sql.append(" ORDER BY ");
                sql.append(table.getField(field).name());
                sql.append(" DESC");
                return this;
            }

            @Override
            public List<M> asList() {
                String sqlString = sql.toString();
                if ( sqlString.endsWith(" WHERE ") )
                    sqlString = sqlString.substring(0, sqlString.length()-7);

                Map<String, List<Object>> result = _db._query(sqlString, values);
                List<Long> ids = result.getOrDefault(EntityTable.ID, Collections.emptyList())
                                            .stream()
                                            .map( o -> (long) o )
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
                sql.append(table.getField(field).name()).append(" ");
                // Then values:
                return (Compare<M, T>) valueCollector;
            }

            @Override
            public <T> Compare<M, T> where( Function<M, Val<T>> selector )
            {
                var field = _selectTableField(selector, model);
                // First sql:
                sql.append(field.name()).append(" ");
                // Then values:
                return (Compare<M, T>) valueCollector;
            }
        };
    }

    @Override
    public void close() {
        _db.close();
    }

    private <T, M extends Model<M>> EntityTableField _selectTableField(
        Function<M, Val<T>> selector,
        Class<M> model
    ) {
        var propSelector = new PropertySelectionProxy(_getTableFor(model));
        var selection = selector.apply((M) Proxy.newProxyInstance(
                                model.getClassLoader(),
                                new Class<?>[]{model},
                                propSelector
                            ));
        if ( selection == null )
            log.error("Selection is null!", new Throwable());
        return propSelector.getSelection().orElseThrow();
    }

    public Map<String, List<String>> query(String sql) {
        Map<String, List<Object>> result = _db._query(sql, Collections.emptyList());
        return result.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(Object::toString)
                                .toList()
                    ));
    }

    long _storeValueAndIncreaseCounter(Value databaseValue) {
        var valueTable = _entityRegistry.getValueTable(databaseValue.getClass())
                .orElseThrow(() -> new IllegalArgumentException(
                        "The value table for " + databaseValue.getClass().getName() + " does not exist!")
                );
        int hashCode = databaseValue.hashCode();
        var existingId = _findIdOfValue(valueTable, databaseValue, hashCode);
        if ( existingId < 0 ) {
            // We need to create a new entry in the value table
            Tuple<Object> rowOfValues = _convertValueToRowOfValues(valueTable, databaseValue, hashCode);
            existingId = _storeEntity(valueTable, databaseValue.getClass(),rowOfValues);
        }
        _modifyUsageCounter(valueTable, existingId, +1);
        return existingId;
    }

    long _findIdOfValue(
        Value value
    ) {
        var valueTable = _entityRegistry.getValueTable(value.getClass())
                                        .orElseThrow(() -> new IllegalArgumentException(
                                                "The value table for " + value.getClass().getName() + " does not exist!"
                                        ));
        int hashCode = value.hashCode();
        long id = _findIdOfValue(valueTable, value, hashCode);
        if ( id < 0 ) {
            throw new IllegalStateException(
                    "The value '" + value + "' does not exist in the database! " +
                    "This is most likely an internal bug in the TopSoil ORM implementation."
                );
        }
        return id;
    }

    long _removeValueAndDecrementCounter(Value databaseValue) {
        var valueTable = _entityRegistry.getValueTable(databaseValue.getClass())
                                            .orElseThrow(() -> new IllegalArgumentException(
                                                "The value table for " + databaseValue.getClass().getName() + " " +
                                                "does not exist!"
                                            ));
        int hashCode = databaseValue.hashCode();
        var existingId = _findIdOfValue(valueTable, databaseValue, hashCode);
        if ( existingId >= 0 ) {
            String sql = "SELECT "+ValueTable.USAGE_FIELD_COUNTER+" FROM " + valueTable.getTableName() +
                         " WHERE "+ModelTable.ID+" = ?";
            Map<String, List<Object>> result = _db._query(sql, Collections.singletonList(existingId));
            if ( result.isEmpty() )
                return -1; // Not found
            if ( result.values().stream().anyMatch( v -> v.size() != 1 ) )
                throw new IllegalStateException();
            var usages = (Integer) result.get(ValueTable.USAGE_FIELD_COUNTER).get(0);
            if ( usages == 1 ) {
                // Delete
                _delete(valueTable.getTableName(), Collections.singletonList(existingId));
            } else {
                // Reduce usage counter
                _modifyUsageCounter(valueTable, existingId, -1);
            }
        }
        return existingId;
    }

    private void _delete(String tableName, List<Long> ids) {
        String sql = "DELETE * FROM " + tableName + " WHERE "+ModelTable.ID+" = ?";
        boolean success = _db._update(sql, Collections.singletonList(ids));
        if (!success ) {
            throw new RuntimeException("Could not delete from " + tableName);
        }
    }

    long _findIdOfValue(
        ValueTable valueTable,
        Value value,
        int hashCode
    ) {
        String sql = "SELECT * FROM " + valueTable.getTableName() + " WHERE "+ValueTable.HASH_FIELD_NAME+" = ?";
        Map<String, List<Object>> result = _db._query(sql, Collections.singletonList(hashCode));
        if ( result.isEmpty() || result.values().stream().allMatch(List::isEmpty) )
            return -1; // Not found
        if ( result.values().stream().anyMatch( v -> v.size() != 1 ) )
            throw new IllegalStateException();

        // Let's check if the value is equal to the value in the database
        Tuple<Object> rowOfValues = _convertValueToRowOfValues(valueTable, value, hashCode);
        List<Object> dbValues = new ArrayList<>();
        for ( var entry : result.entrySet() ) {
            if ( !entry.getKey().equals(EntityTable.ID) && !entry.getKey().equals(ValueTable.USAGE_FIELD_COUNTER) ) {
                dbValues.addAll(entry.getValue());
            }
        }
        if ( !dbValues.equals(rowOfValues.toList()) )
            return -1; // Not found, but the hash code matches

        return (Integer) result.get(EntityTable.ID).get(0);
    }

    private Tuple<Object> _convertValueToRowOfValues(
        ValueTable valueTable,
        Value value,
        int hashCode
    ) {
        List<Object> values = new ArrayList<>();
        values.add(-1); // Dummy id
        values.add(hashCode);
        values.add(0); // Default usages
        for ( EntityTableField field : valueTable.getFields() ) {
            if (field.name().equals(ValueTable.HASH_FIELD_NAME) || field.name().equals(ValueTable.USAGE_FIELD_COUNTER))
                continue;
            if ( field.name().equals(EntityTable.ID) )
                continue;
            try {
                Method method = value.getClass().getMethod(field.baseName());
                Object fieldValue = method.invoke(value);
                if (fieldValue == null) {
                    values.add(null);
                } else if (BasicSQLiteDataBase._isBasicDataType(fieldValue.getClass())) {
                    values.add(fieldValue);
                } else if (fieldValue instanceof Value) {
                    // If the field value is a Value, we need to store it in the database
                    long id = _storeValueAndIncreaseCounter((Value) fieldValue);
                    values.add(id);
                } else {
                    throw new IllegalArgumentException(
                            "The value '" + value + "' has a field '" + field.name() + "' of type '" +
                            fieldValue.getClass().getName() + "' which is not supported!"
                    );
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new IllegalArgumentException(
                        "The value '" + value + "' does not have a method '" + field.name() + "'!",
                        e
                );
            }
        }
        return Tuple.of(Object.class, values);
    }

    private void _modifyUsageCounter(
        ValueTable valueTable,
        long id,
        int delta
    ) {
        String sql = "UPDATE " + valueTable.getTableName() + " SET " + ValueTable.USAGE_FIELD_COUNTER + " = " +
                ValueTable.USAGE_FIELD_COUNTER + " + ? WHERE " + EntityTable.ID + " = ?";
        boolean success = _db._update(sql, List.of(delta, id));
        if ( !success )
            throw new IllegalArgumentException(
                    "Failed to update the usage counter for value with id '" + id + "' in table '" +
                    valueTable.getTableName() + "'!"
            );
    }

}
