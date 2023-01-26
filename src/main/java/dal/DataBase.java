package dal;

import dal.api.Model;
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
public class DataBase extends AbstractDataBase
{
    private final ModelRegistry _modelRegistry = new ModelRegistry();

    DataBase(String location) {
        super(location, "", "");
    }

    DataBase() {
        super("jdbc:sqlite:"+new File("saves/dbs").getAbsolutePath(), "", "");
    }

    public void execute(String sql) {
        _execute(sql);
    }

    private static class ModelRegistry
    {
        private final Map<String, ModelTable> modelTables = new LinkedHashMap<>();

        public ModelRegistry() {}

        public void addTables(List<Class<? extends Model<?>>> modelInterfaces) {

            Set<Class<? extends Model<?>>> distinct = new HashSet<>();
            for ( var modelTable : modelTables.values() )
                modelTable.getModelInterface().ifPresent( modelInterface -> distinct.add(modelInterface) );

            distinct.addAll(modelInterfaces);
            modelInterfaces = new ArrayList<>(distinct);

            Map<String, ModelTable> newModelTables = new LinkedHashMap<>();
            for ( Class<? extends Model<?>> modelInterface : modelInterfaces ) {
                ModelTable modelTable = new BasicModelTable(modelInterface, modelInterfaces);
                newModelTables.put(modelTable.getName(), modelTable);
                modelTable.getFields().forEach(
                        f -> f.getIntermediateTable().ifPresent(
                                t -> newModelTables.put(t.getName(), t)
                        )
                );
            }
            /*
                Now we need to check if there are any circular references
                We do this by checking if there are any cycles in the graph of the model tables
             */
            for ( ModelTable modelTable : newModelTables.values() ) {
                Set<ModelTable> visited = new HashSet<>();
                Set<ModelTable> currentPath = new HashSet<>();
                if ( _hasCycle(modelTable, visited, currentPath, newModelTables) )
                    throw new IllegalArgumentException(
                            "The model " + modelTable.getName() + " has a circular reference!"
                    );
            }

            /*
                Now we need to determine the order in which we create the tables
                We will do this by creating a map between all models as keys and their references
                as values.
                If they are referencing themselves we treat it as no reference.
                We will then fill a list with the models that have no references
                and then remove them from the map and repeat the process until the map is empty.
            */
            List<Class<?>> sortedModels = new ArrayList<>();
            Map<Class<?>, List<Class<?>>> modelReferences = new HashMap<>();
            List<ModelTable> intermediateTables = new ArrayList<>();

            for ( ModelTable modelTable : newModelTables.values() ) {
                List<Class<? extends Model<?>>> referencedModels = modelTable.getReferencedModels();
                List<Class<?>> references = new ArrayList<>();
                for ( Class<? extends Model<?>> referencedModel : referencedModels ) {
                    if ( !referencedModel.equals(modelTable.getModelInterface().orElse(null)) ) {
                        references.add(referencedModel);
                    }
                }
                modelTable.getModelInterface().ifPresent( m -> modelReferences.put(m, references) );
                // If it is not present then it is an intermediate table and we do not need to add it to the map
                // because it is not referenced by any other table, so it can be created at the end.
                if ( modelTable.getModelInterface().isEmpty() ) {
                    intermediateTables.add(modelTable);
                }
            }

            while ( !modelReferences.isEmpty() ) {
                List<Class<?>> modelsWithoutReferences = new ArrayList<>();
                for ( Map.Entry<Class<?>, List<Class<?>>> entry : modelReferences.entrySet() ) {
                    if ( entry.getValue().isEmpty() ) {
                        modelsWithoutReferences.add(entry.getKey());
                    }
                }
                if ( modelsWithoutReferences.isEmpty() ) {
                    throw new IllegalArgumentException(
                            "There are circular references in the model interfaces!"
                    );
                }
                for ( Class<?> modelWithoutReference : modelsWithoutReferences ) {
                    modelReferences.remove(modelWithoutReference);
                    sortedModels.add(modelWithoutReference);
                }
                for ( Map.Entry<Class<?>, List<Class<?>>> entry : modelReferences.entrySet() ) {
                    entry.getValue().removeAll(modelsWithoutReferences);
                }
            }

            for ( Class<?> model : sortedModels ) {
                ModelTable modelTable = newModelTables.get(_tableNameFromClass(model));
                modelTables.put(modelTable.getName(), modelTable);
            }

            // Now we need to add intermediate tables
            for ( ModelTable modelTable : intermediateTables ) {
                modelTables.put(modelTable.getName(), modelTable);
            }
            // We are done!
        }

        private boolean _hasCycle(
                ModelTable modelTable,
                Set<ModelTable> visited,
                Set<ModelTable> currentPath,
                Map<String, ModelTable> newModelTables
        ) {
            if ( visited.contains(modelTable) )
                return false;
            if ( currentPath.contains(modelTable) )
                return true;
            currentPath.add(modelTable);
            for ( Class<? extends Model<?>> referencedModel : modelTable.getReferencedModels() ) {
                if ( _hasCycle(newModelTables.get(_tableNameFromClass(referencedModel)), visited, currentPath, newModelTables) )
                    return true;
            }
            currentPath.remove(modelTable);
            visited.add(modelTable);
            return false;
        }

        public List<ModelTable> getTables() {
            return new ArrayList<>(modelTables.values());
        }

        public List<String> getCreateTableStatements() {
            List<String> statements = new ArrayList<>();
            for ( ModelTable modelTable : modelTables.values() ) {
                statements.add(modelTable.createTableStatement());
            }
            return statements;
        }

        public boolean hasTable(String tableName) {
            return modelTables.containsKey(tableName);
        }

        public ModelTable getTable(String tableName) {
            return modelTables.get(tableName);
        }

        public boolean hasTable(Class<? extends Model<?>> modelInterface) {
            return modelTables.values().stream().anyMatch( t -> t.getModelInterface().isPresent() && t.getModelInterface().get().equals(modelInterface) );
        }

        public ModelTable getTable(Class<? extends Model<?>> modelInterface) {
            return modelTables.values().stream().filter( t -> t.getModelInterface().isPresent() && t.getModelInterface().get().equals(modelInterface) ).findFirst().orElse(null);
        }
    }


    public void dropTablesFor(
            Class<? extends Model<?>>... models
    ) {
        for (Class<? extends Model<?>> model : models)
            _dropTableIfExists(model);
    }

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

    public void dropTable( Class<? extends Model<?>> model ) {
        String tableName = _tableNameFromClass(model);
        _execute("DROP TABLE IF EXISTS " + tableName);

    }

    private void _dropAllTables() {
        List<String> tableNames = this.listOfAllTableNames();
        for ( String tableName : tableNames ) {
            _execute("DROP TABLE IF EXISTS " + tableName);
        }
    }

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

    private static class ModelProxy<T extends Model<T>> implements InvocationHandler
    {
        private final DataBase _dataBase;
        private final ModelTable _modelTable;
        private final int _id;

        public ModelProxy(DataBase db, ModelTable table, int id) {
            _dataBase = db;
            _modelTable = table;
            _id = id;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

            /*
                First let's do the easy stuff: Equals and hashcode
                They are easy because we can just use the id :)
                Let's check:
             */
            if ( methodName.equals("equals") ) {
                if ( args.length != 1 )
                    throw new IllegalArgumentException("The equals method must have exactly one argument!");
                if ( !Model.class.isAssignableFrom(args[0].getClass()) )
                    return false;
                return _id == ((Model<?>) args[0]).id().get();
            }
            if ( methodName.equals("hashCode") ) {
                return _id;
            }
            // Something a little more complicated: toString
            if ( methodName.equals("toString") ) {
                /*
                    Let's not be lazy and actually build a string that contains all the properties!
                 */
                StringBuilder sb = new StringBuilder();
                sb.append(_modelTable.getModelInterface().map(Class::getSimpleName).orElse(_modelTable.getName()));
                sb.append("[");
                for ( var field : _modelTable.getFields() ) {
                    if ( !field.isList() ) {
                        Object o = field.asProperty(_dataBase, _id).orElseNull();
                        String asString;
                        if ( o == null )
                            asString = "null";
                        else if ( o instanceof String )
                            asString = "\"" + o + "\"";
                        else
                            asString = o.toString();

                        sb.append(field.getName());
                        sb.append("=").append(asString);
                        sb.append(", ");
                    }
                    else {
                        sb.append(field.getName());
                        sb.append("=[");
                        for ( Object o : field.asProperties(_dataBase, _id) ) {
                            String asString;
                            if ( o == null )
                                asString = "null";
                            else if ( o instanceof String )
                                asString = "\"" + o + "\"";
                            else
                                asString = o.toString();

                            sb.append(asString);
                            sb.append(", ");
                        }
                        sb.append("], ");
                    }
                }
                // remove the last comma
                if ( _modelTable.getFields().size() > 0 )
                    sb.delete(sb.length() - 2, sb.length());
                sb.append("]");
                return sb.toString();
            }

            ModelField modelField = _modelTable.getField(methodName);
            if ( modelField == null )
                throw new IllegalArgumentException("The model '" + _modelTable.getModelInterface().get().getName() + "' does not have a property named '" + methodName + "'!");
            if ( args != null && args.length != 0 )
                throw new IllegalArgumentException("The model '" + _modelTable.getModelInterface().get().getName() + "' does not have a setter for the property named '" + methodName + "'!");
            if ( method.getReturnType() == void.class )
                throw new IllegalArgumentException("The model '" + _modelTable.getModelInterface().get().getName() + "' does not have a setter for the property named '" + methodName + "'!");

            // Now let's get the property value from the database
            Object toBeReturned;

            if ( Val.class.isAssignableFrom(method.getReturnType()) )
                toBeReturned= modelField.asProperty(_dataBase, _id);
            else if ( Vals.class.isAssignableFrom(method.getReturnType()) )
                toBeReturned= modelField.asProperties(_dataBase, _id);
            else
                throw new IllegalArgumentException("The model '" + _modelTable.getModelInterface().get().getName() + "' does not have a property named '" + methodName + "'!");

            // Now let's check if the property is of the correct type
            if ( !method.getReturnType().isAssignableFrom(toBeReturned.getClass()) )
                throw new IllegalArgumentException("Failed to create a proxy for the model '" + _modelTable.getModelInterface().get().getName() + "' because the property '" + methodName + "' is of type '" + toBeReturned.getClass().getName() + "' but the getter is of type '" + method.getReturnType().getName() + "'!");

            return toBeReturned;
        }
    }

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

    public <M extends Model<M>> M create(Class<M> model) {
        // First let's verify that the model is indeed a model
        if ( !Model.class.isAssignableFrom(model) )
            throw new IllegalArgumentException("The provided class is not a model!");

        // Now let's verify that the table exists
        if ( !doesTableExist(_tableNameFromClass(model)) )
            throw new IllegalArgumentException("The table for the model '" + model.getName() + "' does not exist!");

        // Now let's create the model
        ModelTable modelTable = _modelRegistry.getTable(model);
        List<ModelField> fields = modelTable.getFields();
        List<Object> defaultValues = modelTable.getDefaultValues();
        List<String> fieldNames    = fields.stream().map(ModelField::getName).collect(Collectors.toList());
        /*
            Now there might be a problem here because some model fields might not actually exist
            in the table explicitly. Namely, if the model references multiple other models
            through a Vars or Vals field!
            So we need to check for that and remove those fields from the list of fields
        */
        for ( int i = fields.size()-1; i >= 0; i-- ) {
            ModelField field = fields.get(i);
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

    public <M extends Model<M>> void remove(M model) {
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

    interface Junction<M extends Model<M>> extends Get<M> {

        <T> WhereField<M, T> and(Class<? extends Val<T>> field);

        <T> WhereField<M, T> or(Class<? extends Val<T>> field);

        default List<M> limit(int limit) {
            return toList().subList(0, limit);
        }

        Get<M> orderAscendingBy(Class<? extends Val<?>> field);

        Get<M> orderDescendingBy(Class<? extends Val<?>> field);

    }

    interface Get<M extends Model<M>> {

        List<M> toList();

    }

    interface Where<M extends Model<M>> extends Get<M> {

        <T> WhereField<M, T> where(Class<? extends Val<T>> field);

    }

    interface WhereField<M extends Model<M>, T> {

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

    public <M extends Model<M>> Where<M> select(Class<M> model) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(_tableNameFromClass(model)).append(" WHERE ");
        ModelTable table = _modelRegistry.getTable(model);
        List<Object> values = new ArrayList<>();
        Junction[] junc = {null};
        WhereField<M, Object> valueCollector = new WhereField<M, Object>() {
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
            public <T> WhereField<M, T> and(Class<? extends Val<T>> field) {
                sql.append(" AND ");
                sql.append(table.getField(field).getName());
                return (WhereField<M, T>) valueCollector;
            }

            @Override
            public <T> WhereField<M, T> or(Class<? extends Val<T>> field) {
                sql.append(" OR ");
                sql.append(table.getField(field).getName());
                return (WhereField<M, T>) valueCollector;
            }

            @Override
            public Get<M> orderAscendingBy(Class<? extends Val<?>> field) {
                sql.append(" ORDER BY ");
                sql.append(table.getField(field).getName());
                sql.append(" ASC");
                return this;
            }

            @Override
            public Get<M> orderDescendingBy(Class<? extends Val<?>> field) {
                sql.append(" ORDER BY ");
                sql.append(table.getField(field).getName());
                sql.append(" DESC");
                return this;
            }

            @Override
            public List<M> toList() {
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
            public <T> WhereField<M, T> where(Class<? extends Val<T>> field) {
                // First sql:
                sql.append(table.getField(field).getName()).append(" ");
                // Then values:
                return (WhereField<M, T>) valueCollector;
            }

            @Override
            public List<M> toList() {
                return junc[0].toList();
            }
        };
    }

}
