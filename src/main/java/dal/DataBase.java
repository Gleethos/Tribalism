package dal;

import org.slf4j.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 *  This is a generic ORM mapper for SQLite databases
 *  which almost completely hides the database from the
 *  rest of the application.
 *  This also almost entirely avoids the usage of annotations
 *  by using reflection to get the information needed.
 *  So when creating a new table the name is based on the class name
 *  and the fields are based on the fields in the class.
 *  The only thing that needs to be done is to create a class
 *  with the fields that you want to be in the database.
 *  You also do not need to create an id field as this is done
 *  automatically (although it can be specified).
 *
 */
public class DataBase extends AbstractDataBase
{
    Logger log = org.slf4j.LoggerFactory.getLogger(DataBase.class);

    private final DataBaseRegistry _modelSummaries = new DataBaseRegistry();


    DataBase(String location) {
        super(location, "", "");
    }

    DataBase() {
        super("jdbc:sqlite:"+new File("saves/dbs").getAbsolutePath(), "", "");
    }

    private String _tableNameFromClass(Class<?> clazz) {
        String tableName = clazz.getName();
        // We replace the package dots with underscores:
        tableName = tableName.replaceAll("\\.", "_");
        return tableName + "_table";
    }

    private Optional<Class<?>> _classFromTableName(String tableName) {
        try {
            tableName = tableName.replace("_table", "");
            tableName = tableName.replaceAll("_", ".");
            return Optional.of(Class.forName(tableName));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    public List<Class<?>> getTables() {
        List<String> tableNames = this.listOfAllTableNames();
        List<Class<?>> classes = new ArrayList<>();
        for (String tableName : tableNames) {
            Optional<Class<?>> clazz = this._classFromTableName(tableName);
            if (clazz.isPresent()) {
                classes.add(clazz.get());
            }
            else {
                log.warn("Could not find class for table name: " + tableName);
            }
        }
        return classes;
    }

    public void dropTablesFor(
            Class<?>... models
    ) {
        for (Class<?> model : models)
            _dropTableIfExists(model);
    }

    public void dropAllTables() {
        _dropAllTables();
    }

    private void _createTableIfNotExists( Class<?> model ) {
        if ( !doesTableExist(_tableNameFromClass(model)) )
            createTable(model);
    }

    private void _dropTableIfExists(Class<?> model) {
        if (doesTableExist(_tableNameFromClass(model)))
            dropTable(model);
    }

    public void dropTable( Class<?> model ) {
        String tableName = _tableNameFromClass(model);
        _execute("DROP TABLE IF EXISTS " + tableName);

    }

    private void _dropAllTables() {
        List<String> tableNames = this.listOfAllTableNames();
        for ( String tableName : tableNames ) {
            _execute("DROP TABLE IF EXISTS " + tableName);
        }
    }

    public void insert( Object model ) {
        String tableName = _tableNameFromClass(model.getClass());
        var fields = model.getClass().getDeclaredFields();
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ");
        sql.append(tableName);
        sql.append(" (");
        for (var f : fields) {
            if (f.getName().equals("id")) {
                try {
                    // if it is null ot 0 then it is not set
                    if (f.get(model) == null || f.getInt(model) == 0)
                        continue;
                    else
                        throw new IllegalArgumentException(
                            "Cannot insert an object with an id already set"
                        );
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            sql.append(f.getName());
            sql.append(", ");
        }
        // remove the last comma and space
        sql.delete(sql.length() - 2, sql.length());
        sql.append(") VALUES (");
        for (var f : fields) {
            if (f.getName().equals("id"))
                continue;
            sql.append("?, ");
        }
        // remove the last comma and space
        sql.delete(sql.length() - 2, sql.length());
        sql.append(");");
        _execute(sql.toString(), model);
    }

    /**
     *  Note that you can only update a model if it has an id set!
     *
     * @param model
     */
    public void update( Object model, String... fields ) {
        String tableName = _tableNameFromClass(model.getClass());
        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ");
        sql.append(tableName);
        sql.append(" SET ");
        for (String field : fields) {
            sql.append(field);
            sql.append(" = ?, ");
        }
        // remove the last comma and space
        sql.delete(sql.length() - 2, sql.length());
        sql.append(" WHERE id = ?;");
        _execute(sql.toString(), model);
    }

    /**
     *  Note that you can only update a model if it has an id set!
     *
     * @param model
     */
    public void update( Object model ) {
        List<String> fieldNames = new ArrayList<>();
        var fields = model.getClass().getDeclaredFields();
        for (var f : fields) {
            if (f.getName().equals("id"))
                continue;
            fieldNames.add(f.getName());
        }
        update(model, fieldNames.toArray(new String[0]));
    }



    private void _execute( String sql, Object model ) {
        var field = model.getClass().getDeclaredFields();
        List<Object> values = new ArrayList<>();
        for (var f : field) {
            if (f.getName().equals("id"))
                continue;
            f.setAccessible(true);
            try {
                values.add(f.get(model));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        _execute(sql, values);
    }

    public <T> Optional<T> get( Class<T> model, int id ) {
        String tableName = _tableNameFromClass(model);
        String sql = "SELECT * FROM " + tableName + " WHERE id = ?;";

        // The keys are column names and the values are the values of the columns
        Map<String, List<Object>> result = _query(sql, List.of(id));

        if (result.isEmpty())
            return Optional.empty();

        // There should only be one row, if there are more or less then something is wrong
        if (result.get("id").size() != 1)
            throw new RuntimeException("There should only be one row, if there are more or less then something is wrong");

        return Optional.ofNullable(_fromMapToModel(0, model, result));
    }

    private <T> T _fromMapToModel( int row, Class<T> model, Map<String, List<Object>> result ) {
        try {
            T instance = model.getConstructor().newInstance();
            var fields = model.getDeclaredFields();
            for ( var f : fields ) {
                f.setAccessible(true);
                if (f.getName().equals("id")) {
                    f.set(instance, result.get("id").get(row));
                }
                else {
                    /*
                        Ok, so usually the value we get from the database is a simple primitive type
                        like an int or a String. But sometimes it is a more complex type like a
                        reference to another model, or even a list or set of models. So we need to
                        check the type of the field and then handle it accordingly.
                     */
                    if ( _isBasicDataType( f.getType() ) )
                        f.set(instance, result.get(f.getName()).get(row));
                    else if ( _modelSummaries.contains( f.getType() ) ) {
                        String fieldName = "fk_" + f.getName() + "_id";
                        int id = (int) result.get(fieldName).get(row);
                        // Now we have to load the model from the database
                        Object m = get(f.getType(), id).orElseThrow();
                        f.set(instance, m);
                    }
                    else if ( _isListOrSet( f.getType() ) ) {
                        // We need to get the type of the list or set
                        Type type = f.getGenericType();
                        if (type instanceof ParameterizedType) {
                            ParameterizedType pType = (ParameterizedType) type;
                            Type[] fieldArgTypes = pType.getActualTypeArguments();
                            if (fieldArgTypes.length != 1)
                                throw new RuntimeException("List or Set must have exactly one type argument");
                            Class<?> fieldArgType = (Class<?>) fieldArgTypes[0];
                            if ( _modelSummaries.contains( fieldArgType ) ) {
                                /*
                                    Ok, so we have a list or set of models.
                                    The problem is that in our database we don't actually store a
                                    list or set of models, but instead the models we are referencing here
                                    each have a foreign key to this model which is simply named after this field
                                    variable name. So we need to get the name of the foreign key column
                                    and then query the database for all the models that have a foreign key
                                    that matches this model's id.
                                 */
                                String fkColumnName = "fk_" + f.getName() + "_id";
                                String sql = "SELECT * FROM " + _tableNameFromClass(fieldArgType) + " WHERE " + fkColumnName + " = ?;";
                                int thisId = (int) result.get("id").get(row);
                                Map<String, List<Object>> queryResult = _query(sql, List.of(thisId));
                                if (queryResult.isEmpty()) {
                                    // We set an empty list or set
                                    if ( List.class.isAssignableFrom( f.getType() ) )
                                        f.set(instance, new ArrayList<>());
                                    else
                                        f.set(instance, new HashSet<>());
                                }
                                else {
                                    // We set a list or set with the models we got from the database
                                    if ( List.class.isAssignableFrom( f.getType() ) ) {
                                        List<Object> list = new ArrayList<>();
                                        for (int i = 0; i < queryResult.get("id").size(); i++) {
                                            list.add(_fromMapToModel(i, fieldArgType, queryResult));
                                        }
                                        f.set(instance, list);
                                    }
                                    else {
                                        Set<Object> set = new HashSet<>();
                                        for (int i = 0; i < queryResult.get("id").size(); i++) {
                                            set.add(_fromMapToModel(i, fieldArgType, queryResult));
                                        }
                                        f.set(instance, set);
                                    }
                                }
                            }
                            else
                                throw new RuntimeException("Unknown type of list or set");
                        }
                        else
                            throw new RuntimeException("Unknown type of list or set");
                    }
                    else
                        throw new RuntimeException("Unknown type of field");
                }
            }
            return instance;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }

    private <T> List<T> _fromMapToListOfModels( Class<T> model, Map<String, List<Object>> result ) {
        if ( result.isEmpty() )
            return Collections.emptyList();
        List<T> list = new ArrayList<>();
        for (int i = 0; i < result.get("id").size(); i++) {
            list.add(_fromMapToModel(i, model, result));
        }
        return list;
    }

    public <T> List<T> selectAll(Class<T> model) {
        String tableName = _tableNameFromClass(model);
        String sql = "SELECT * FROM " + tableName + ";";
        Map<String, List<Object>> result = _query(sql);
        return _fromMapToListOfModels(model, result);
    }

    class ModelSummary {

        private final Class<?> modelType;
        private final String tableName;
        private final List<Class<?>> allForeignRefs = new ArrayList<>(); // Basically foreign keys referencing "id"

        private final List<Class<?>> specifiedForeignRefs = new ArrayList<>(); // Basically foreign keys referenced by "id"
        private final List<Field> specifiedForeignRefsFields = new ArrayList<>();

        private final List<Class<?>> oneToManyModelRefs = new ArrayList<>(); // One to many foreign keys referencing "id", basically a list of models
        private final List<Field> oneToManyRefFields = new ArrayList<>(); // The fields that are one to many foreign keys


        // This is the above but received from the other side of the relationship:
        private final List<Field> receivedOneToManyRefFields = new ArrayList<>(); // The fields that are one to many foreign keys


        public ModelSummary(Class<?> modelType, List<Class<?>> otherModels) {
            this.modelType = modelType;
            this.tableName = _tableNameFromClass(modelType);
            var fields = modelType.getDeclaredFields();
            for (var f : fields) {
                var type = f.getType();
                // First let's check if it is a list:
                if (type.equals(List.class)) {
                    // Now we need to get the type of the list
                    var genericType = f.getGenericType();
                    if (genericType instanceof ParameterizedType) {
                        var parameterizedType = (ParameterizedType) genericType;
                        var actualTypeArguments = parameterizedType.getActualTypeArguments();
                        if (actualTypeArguments.length == 1) {
                            var actualTypeArgument = actualTypeArguments[0];
                            if (actualTypeArgument instanceof Class) {
                                var actualTypeArgumentClass = (Class<?>) actualTypeArgument;
                                if (otherModels.contains(actualTypeArgumentClass)) {
                                    oneToManyModelRefs.add(actualTypeArgumentClass);
                                    oneToManyRefFields.add(f);
                                }
                                else {
                                    if ( _isBasicDataType(actualTypeArgumentClass) )
                                        throw new RuntimeException("A list of basic data types cannot be persisted as column entries in a database!");
                                    else
                                        throw new RuntimeException("The type of the list is not a model");
                                }
                            }
                            else
                                throw new RuntimeException("The type of the list is not a class");
                        }
                        else
                            throw new RuntimeException("We only support one generic type argument for lists");
                    }
                    else
                        throw new RuntimeException("The type of the list is not a class");
                }
                else if (otherModels.contains(type)) {
                    allForeignRefs.add(type);
                    specifiedForeignRefs.add(type);
                    specifiedForeignRefsFields.add(f);
                }
                else if (_isBasicDataType(type)) {
                    // Do nothing, this will be a regular column entry
                }
                else {
                    throw new RuntimeException("Unknown type: " + type + "! Cannot persist unknown types!");
                }
            }
        }

        public List<Class<?>> getAllForeignReferences() {
            return allForeignRefs;
        }

        public List<Class<?>> getSpecifiedForeignRefs() {
            return Collections.unmodifiableList(specifiedForeignRefs);
        }

        public List<Field> getSpecifiedForeignRefsFields() {
            return Collections.unmodifiableList(specifiedForeignRefsFields);
        }

        public List<Class<?>> getOneToManyModelRefs() {
            return Collections.unmodifiableList(oneToManyModelRefs);
        }

        public List<Field> getReceivedOneToManyRefFields() {
            return receivedOneToManyRefFields;
        }

        public List<Field> getOneToManyRefFields() {
            return Collections.unmodifiableList(oneToManyRefFields);
        }

        public String getTableName() {
            return tableName;
        }

        public Class<?> getModelType() {
            return modelType;
        }

    }

    private static boolean _isBasicDataType(Class<?> type) {
        return
                type.equals(String.class) ||
                type.equals(int.class) ||
                type.equals(boolean.class) ||
                type.equals(Integer.class) ||
                type.equals(Boolean.class) ||
                type.equals(Long.class) ||
                type.equals(long.class) ||
                type.equals(Double.class) ||
                type.equals(double.class) ||
                type.equals(Float.class) ||
                type.equals(float.class) ||
                type.equals(Short.class) ||
                type.equals(short.class) ||
                type.equals(Byte.class) ||
                type.equals(byte.class) ||
                type.equals(Character.class) ||
                type.equals(char.class);
    }

    private boolean _isListOrSet(Class<?> type) {
        return type.equals(List.class) || type.equals(Set.class);
    }

    public void createTablesFor(
            Class<?>... models
    ) {
        /*
            So we could just do:
            for (Class<?> model : models)
                _createTableIfNotExists(model);

            But the classes we get above might have certain relationships with each other
            and we want to make sure that the tables are created in the correct order
            as well as resolve any foreign keys.
            So let's say we have a class called User and a class called Post
            and User has a list of Posts
            then we want to make sure that the Post table is created first
            and then the User table is created second
            and that the foreign key is set up correctly, meaning that the Post table
            has a column called fk_user_id that is a foreign key referencing the User id (primary key).
            On the other hand, when a model type has a single field referencing another model type
            then the former type should have a foreign key referencing the latter type.
            So if User has a reference to Address, then we want to make sure that the Address table is created first
            as well as give the User table a foreign key to Address.

            Enough talk, let's get to work!
            How do we solve this?
            Here some ruff outline:
            1. Create a ModelSummary objects that keep track of the interdependencies between the models.
            2. Store the ModelSummary in a hashmap where the key is the model type and the value is the ModelSummary.
            3. Note which models are referenced by other models in the summary.
            4. Create the tables in the correct order.
         */

        for ( Class<?> model : models ) {
            _modelSummaries.put(model, new ModelSummary(model, Arrays.asList(models)));
        }

        // Now we have a list of models to create and a hashmap of model summaries
        // Before we create the tables, we need to revisit the stored model summaries and
        // resolve the interdependencies between the models.
        // The "1" in 1:n relationships is already resolved in the ModelSummary constructor,
        // but the "n" in 1:n relationships is not, so we need to resolve that.

        // First let's resolve the 1:n relationships
        for ( Class<?> model : models ) {
            var modelSummary = _modelSummaries.get(model);
            for ( Class<?> oneOfManyModel : modelSummary.getOneToManyModelRefs() ) {
                var oneOfMany = _modelSummaries.get(oneOfManyModel);
                oneOfMany.getAllForeignReferences().add(model);
                int index = modelSummary.getOneToManyModelRefs().indexOf(oneOfManyModel);
                oneOfMany.getReceivedOneToManyRefFields().add(modelSummary.getOneToManyRefFields().get(index));
            }
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
        // First let's fill the map
        for ( Class<?> model : models ) {
            var modelSummary = _modelSummaries.get(model);
            var references = new ArrayList<Class<?>>();
            references.addAll(modelSummary.getAllForeignReferences());
            references.remove(model); // We don't want to reference ourselves because a table "knows about itself".
            modelReferences.put(model, references);
        }

        // Now let's fill the list
        while ( !modelReferences.isEmpty() ) {
            var modelsWithoutReferences = new ArrayList<Class<?>>();
            for ( Class<?> model : modelReferences.keySet() ) {
                if ( modelReferences.get(model).isEmpty() )
                    modelsWithoutReferences.add(model);
            }
            if ( modelsWithoutReferences.isEmpty() )
                throw new RuntimeException("There is a circular reference between the models!");
            for ( Class<?> model : modelsWithoutReferences ) {
                sortedModels.add(model);
                modelReferences.remove(model);
            }
            for ( Class<?> model : modelReferences.keySet() ) {
                modelReferences.get(model).removeAll(modelsWithoutReferences);
            }
        }

        // Now we have a list of models sorted in the correct order
        // Let's create the tables!

        for ( Class<?> model : sortedModels ) {
            _createTableIfNotExists(model);
        }
    }


    public void createTable( Class<?> model ) {
        _createTable(model);
    }


    private void _createTable( Class<?> model ) {
        var modelSummary = _modelSummaries.get(model);
        var tableName = modelSummary.getTableName();
        var field = model.getDeclaredFields();
        /*
            We need to create a table with the following columns:
            - id (primary key, auto increment)
            - all the primitive fields in the model
            - all the foreign keys (stored in the model summary)
            We ignore
            - Lists and Sets containing other models
            We raise an exception if
            - There is a field that is not a primitive type
              and it is not a List or Set containing other models
        */
        var sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ");
        sql.append(tableName);
        sql.append(" (");
        sql.append("id INTEGER PRIMARY KEY AUTOINCREMENT"); // This is always the first column!
        for ( var f : field ) {
            var type = f.getType();
            // Let's ignore the id field
            if ( f.getName().equals("id") ) {
                // We expect the id field to be an int
                if ( !type.equals(int.class) )
                    throw new RuntimeException("The id field must be an int!");

                continue;
            }
            if ( _isBasicDataType(type) ) {
                sql.append(", ");
                sql.append(f.getName());
                sql.append(" ");
                sql.append(_fromJavaTypeToDBType(type));
            } else if ( type.equals(List.class) || type.equals(Set.class) ) {
                // We ignore these
                // But we also check that the type of the list/set is a model
                var genericType = f.getGenericType();
                if ( genericType instanceof ParameterizedType parameterizedType ) {
                    var actualTypeArguments = parameterizedType.getActualTypeArguments();
                    if ( actualTypeArguments.length != 1 )
                        throw new RuntimeException("The list/set field must have exactly one type argument!");
                    var typeArgument = actualTypeArguments[0];
                    if ( !(typeArgument instanceof Class) )
                        throw new RuntimeException("The type argument must be a class!");
                    var typeArgumentClass = (Class<?>) typeArgument;
                    if ( !_modelSummaries.contains(typeArgumentClass) )
                        throw new RuntimeException("The type argument must be a model!");
                } else {
                    throw new RuntimeException("The list/set field must have exactly one type argument!");
                }
            } else {
                // We raise an exception if it is not a primitive type
                // and it is not a List or Set containing other models
                throw new RuntimeException(
                        "The field " + f.getName() + " in the model " + model.getName() +
                        " is not a primitive type and it is not a List or Set containing other models!"
                );
            }
        }
        for ( var f : modelSummary.getSpecifiedForeignRefsFields() ) {
            sql.append(", ");
            sql.append("fk_");
            sql.append(f.getName());
            sql.append("_id");
            sql.append(" INTEGER");
        }
        for ( var f : modelSummary.getReceivedOneToManyRefFields() ) {
            sql.append(", ");
            sql.append("fk_");
            sql.append(f.getName());
            sql.append("_id");
            sql.append(" INTEGER");
        }
        sql.append(")");

        _execute(sql.toString());
    }

}
