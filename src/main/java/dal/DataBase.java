package dal;

import swingtree.api.mvvm.*;

import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;
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

    private enum FieldKind {
        ID,
        VALUE,
        FOREIGN_KEY,
        INTERMEDIATE_TABLE
    }

    private static class ModelProperty implements Var<Object> {

        private final DataBase _dataBase;
        private final int _id;
        private final String _fieldName;
        private final String _tableName;
        private final Class<?> _propertyValueType;

        private ModelProperty(DataBase dataBase, int id, String fieldName, String tableName, Class<?> propertyValueType) {
            _dataBase = dataBase;
            _id = id;
            _fieldName = fieldName;
            _tableName = tableName;
            _propertyValueType = propertyValueType;
        }

        @Override
        public Object orElseThrow() {
            Object o =  _get();
            if ( o == null )
                throw new NoSuchElementException("No value present");
            return o;
        }

        private Object _get() {
            Object value;
            StringBuilder select = new StringBuilder();
            select.append("SELECT ").append(_fieldName)
                    .append(" FROM ").append(_tableName)
                    .append(" WHERE id = ?");

            Map<String, List<Object>> result = _dataBase._query(select.toString(), Collections.singletonList(_id));
            if (result.isEmpty())
                throw new IllegalStateException("No result for query: '" + select + "' with id: " + _id );
            else {
                List<Object> values = result.get(_fieldName);
                if (values.isEmpty())
                    throw new IllegalStateException("Failed to find table entry for id " + _id);
                else if (values.size() > 1)
                    throw new IllegalStateException("Found more than one table entry for id " + _id);
                else
                    value = values.get(0);
            }

            if ( !Model.class.isAssignableFrom(_propertyValueType) )
                return value;
            else {
                // A foreign key to another model! We already have the id, so we can just create the model
                // and return it.
                // But first let's check if the object we found is not null and actually a number
                if ( value == null )
                    throw new IllegalStateException("The foreign key value is null");
                else if ( !Number.class.isAssignableFrom(value.getClass()) )
                    throw new IllegalStateException("The foreign key value is not a number");
                else {
                    // We have a number, so we can find the model
                    int foreignKeyId = ((Number) value).intValue();
                    Class<? extends Model<?>> foreignKeyModelClass = (Class<? extends Model<?>>) _propertyValueType;
                    value = _dataBase.select((Class) foreignKeyModelClass, foreignKeyId);
                    if ( value == null )
                        throw new IllegalStateException("Failed to find model of type " + foreignKeyModelClass.getName() + " with id " + foreignKeyId);
                    else
                        return value;
                }
            }
        }

        @Override
        public Var<Object> set(Object newItem) {
            if ( !(newItem instanceof Model<?>) ) {
                String update = "UPDATE " + _tableName +
                                " SET " + _fieldName +
                                " = ? WHERE id = ?";
                boolean success = _dataBase._update(update, Arrays.asList(newItem, _id));
                if ( !success )
                    throw new IllegalStateException("Failed to update table entry for id " + _id);
            } else {
                // We have a model, so we need to update the foreign key
                Model<?> model = (Model<?>) newItem;
                StringBuilder update = new StringBuilder();
                update.append("UPDATE ");
                update.append(_tableName);
                update.append(" SET ");
                update.append(_fieldName);
                update.append(" = ? WHERE id = ?");
                boolean success = _dataBase._update(update.toString(), Arrays.asList(model.id().get(), _id));
                if ( !success )
                    throw new IllegalStateException("Failed to update table entry for id " + _id);
            }
            return this;
        }

        @Override
        public Var<Object> withId(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Var<Object> onAct(Action<ValDelegate<Object>> action) { return this; }

        @Override
        public Var<Object> act() { return this; }

        @Override
        public Var<Object> act(Object newValue) { return set(newValue); }

        @Override
        public Object orElseNullable(Object other) {
            var o = _get();
            if ( o == null )
                return other;
            else
                return o;
        }

        @Override public boolean isPresent() { return orElseNull() != null; }

        @Override
        public <U> Val<U> viewAs(Class<U> type, Function<Object, U> mapper) {
            throw new UnsupportedOperationException();
        }

        @Override public String id() { return ""; }

        @Override
        public Class<Object> type() { return (Class<Object>) _propertyValueType; }

        @Override
        public Val<Object> onShow(Action<ValDelegate<Object>> displayAction) { return this; }

        @Override public Val<Object> show() { return this; }

        @Override public boolean allowsNull() { return true; }
    }

    private static class ModelField {

        private final Method method;
        private final Class<? extends Model<?>> ownerModelClass;
        private final Class<?> propertyValueType;
        private final Class<?> propertyType;
        private final FieldKind kind;
        private final List<Class<? extends Model<?>>> otherModels;

        private ModelField(Method method, Class<? extends Model<?>> ownerClass, List<Class<? extends Model<?>>> otherModels) {
            this.method = method;
            this.ownerModelClass = ownerClass;
            this.propertyType = method.getReturnType();
            this.otherModels = Collections.unmodifiableList(otherModels);

            // First we check if the return type is a subclass of Val or Vals
            boolean isVal = Val.class.isAssignableFrom(propertyType);
            boolean isVals = Vals.class.isAssignableFrom(propertyType);

            if ( !isVal && !isVals )
                throw new IllegalArgumentException(
                        "The return type of the method " + method.getName() + " is not a subclass of " +
                        "either " + Val.class.getName() + " or " + Vals.class.getName() + "."
                    );

            // Great that is correct! But now we have another requirement:
            /*
                So a model interface might look something like this:
                public interface Person extends Model<Person> {
                    interface Name extends Var<String> {} // Val is a property type with getter and setter
                    interface Age extends Var<Integer> {}
                    interface Addresses extends Vars<Address> {} // Vars is a property type with getter and setter wrapping multiple values
                    Name name();
                    Age age();
                }
                Not only do we expect that the return type of the method is a subclass of Val...
                ...we also expect it to be declared as an inner interface of the model interface!
                This is because of readability and coherence and also
                because we might need to be able to get the name of the field from the interface.

                Ok, enough talk, let's check if the return type is declared as an inner interface of the model interface.
            */
            Class<?> declaringClass = method.getDeclaringClass();
            Class<?> outerClassOfNestedClass = propertyType.getDeclaringClass();
            if ( !declaringClass.equals(outerClassOfNestedClass) )
                throw new IllegalArgumentException(
                        "The return type of the method " + method.getName() + " is not declared as an inner interface of " + declaringClass.getName()
                    );

            // Now we can get the type of the value of the property
            // This is a generic type parameter of the Val interface
            // So in this example for "Name" it would be String
            // and for "Age" it would be Integer:
            /*
                public interface Person extends Model<Person> {
                    interface Name extends Val<String> {} // Val is a property type with getter and setter
                    interface Age extends Val<Integer> {}
                    Name name();
                    Age age();
                }
             */
            TypeVariable<?>[] typeParameters = propertyType.getTypeParameters();
            if ( typeParameters.length != 0 )
                throw new IllegalArgumentException(
                        "The return type of the method " + method.getName() + " may not have generic parameters!"
                    );
            Type[] genericInterfaces = propertyType.getGenericInterfaces();
            if ( genericInterfaces.length != 1 )
                throw new IllegalArgumentException(
                        "The return type of the method " + method.getName() + " must implement exactly one interface!"
                    );

            Type genericInterface = genericInterfaces[0];
            Type[] actualTypeArguments = ((ParameterizedType) genericInterface).getActualTypeArguments();
            propertyValueType = (Class<?>) actualTypeArguments[0];

            // Now we need to determine the kind of the field, here are the possibilities:
            /*
                public interface Person extends Model<Person> {
                    interface Address extends Var<Address> {}  // Kind: FOREIGN_KEY
                    interface Name extends Var<String> {}      // Kind: VALUE
                    interface Age extends Var<Integer> {}      // Kind: VALUE
                    interface Children extends Vars<Person> {} // Kind: INTERMEDIATE_TABLE
                }
                // ... and ...
                public interface Model<M> {
                    interface Id extends Val<Integer> {} // Kind: ID
                    Id id();
                    ...
                }
             */

            // First we check if the field is an ID field
            if ( method.getName().equals("id") ) {
                if ( !propertyType.equals(Model.Id.class) )
                    throw new IllegalArgumentException(
                            "The return type of the method " + method.getName() + " is not " + Model.Id.class.getName()
                        );
                kind = FieldKind.ID;
            }
            // Then we check if the field is a foreign key field
            else if ( isVal ) {
                if ( Model.class.isAssignableFrom(propertyValueType) ) {
                    if ( otherModels.contains(propertyValueType) ) {
                        kind = FieldKind.FOREIGN_KEY;
                    }
                    else
                        throw new IllegalArgumentException(
                                "The return type of the method " + method.getName() + " is a foreign key to a model " +
                                "however the provided model type '"+propertyValueType.getName()+"' is not known " +
                                "by the database!"
                            );
                }
                else if (_isBasicDataType(propertyValueType)) {
                    kind = FieldKind.VALUE;
                }
                else
                    throw new IllegalArgumentException(
                            "The return type of the method " + method.getName() + " is not a basic data type " +
                            "and is not a foreign key to a model!"
                        );
            }
            // Then we check if the field is an intermediate table field
            else if ( isVals ) {
                if ( otherModels.contains(propertyValueType) ) {
                    kind = FieldKind.INTERMEDIATE_TABLE;
                }
                else {
                    if ( _isBasicDataType(propertyValueType) )
                        throw new IllegalArgumentException(
                                "List of basic data types cannot be modelled as table fields."
                            );
                    else
                        throw new IllegalArgumentException(
                            "The type '"+propertyType.getName()+"' of the property returned by " +
                            "method " + method.getName() + " is not a known model type."
                        );
                }
            }
            else
                throw new IllegalArgumentException(
                        "The return type of the method " + method.getName() + " is not a subclass " +
                        "of " + Val.class.getName() + " or " + Vals.class.getName() + " with one type parameter"
                    );


        }

        public String getName() {
            if ( this.kind == FieldKind.FOREIGN_KEY )
                return "fk_" + method.getName() + "_id";
            return method.getName();
        }

        public boolean isField(String name) {
            return method.getName().equals(name);
        }

        public Class<?> getType() {
            return propertyValueType;
        }

        public boolean isList() {
            return Vals.class.isAssignableFrom(propertyType);
        }

        public FieldKind getKind() {
            return kind;
        }

        public boolean requiresIntermediateTable() {
            return kind == FieldKind.INTERMEDIATE_TABLE;
        }

        public boolean isForeignKey() {
            return kind == FieldKind.FOREIGN_KEY;
        }

        public String toTableFieldStatement() {
            return getName() + " " + _fromJavaTypeToDBType(propertyValueType);
        }

        public Optional<ModelTable> getIntermediateTable() {
            if ( requiresIntermediateTable() )
                return Optional.of(new ModelTable(){
                    @Override
                    public String getName() { return ModelField.this.getName() + "_list_table"; }

                    @Override
                    public List<ModelField> getFields() { return Collections.emptyList(); }

                    @Override
                    public List<Class<? extends Model<?>>> getReferencedModels() {
                        Class<?> thisTableClass = ModelField.this.method.getDeclaringClass();
                        Class<?> otherTableClass = ModelField.this.propertyValueType;
                        return Arrays.asList((Class<? extends Model<?>>) thisTableClass, (Class<? extends Model<?>>) otherTableClass);
                    }

                    @Override
                    public String createTableStatement() {
                        /*
                            Simple:
                            - id
                            - foreign_key pointing to the model table of the model to which the list belongs
                            - foreign_key pointing to the model of the property type of the list
                         */
                        Class<?> thisTableClass = ModelField.this.method.getDeclaringClass();
                        Class<?> otherTableClass = ModelField.this.propertyValueType;
                        String thisTable  = _tableNameFromClass(thisTableClass);
                        String otherTable = _tableNameFromClass(otherTableClass);
                           return "CREATE TABLE " + getName() + " (\n" +
                                  "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                                  "    fk_" + thisTable + "_id INTEGER NOT NULL,\n" +
                                  "    fk_" + otherTable + "_id INTEGER NOT NULL,\n" +
                                  "    FOREIGN KEY (fk_" + thisTable + "_id) REFERENCES " + thisTable + "(id),\n" +
                                  "    FOREIGN KEY (fk_" + otherTable + "_id) REFERENCES " + otherTable + "(id)\n" +
                                  ");";
                    }

                    @Override
                    public List<Object> getDefaultValues() {
                        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                    }

                });
            else
                return Optional.empty();
        }

        public Val<Object> asProperty(DataBase db, int id) {
            var prop = new ModelProperty(db, id, this.getName(), _tableNameFromClass(ownerModelClass), propertyValueType);

            Class<?> propertyType = ModelField.this.propertyType;

            // Let's create the proxy:
            return  (Val<Object>) Proxy.newProxyInstance(
                    propertyType.getClassLoader(),
                    new Class[]{propertyType},
                    (proxy, method, args) -> {
                        // We simply delegate to the property
                        return method.invoke(prop, args);
                    }
            );
        }

        public Optional<String> asSqlColumn() {
            String name = getName();
            if ( !Model.class.isAssignableFrom(propertyValueType) ) {
                String properties = " NOT NULL";
                if ( name.equals("id") )
                    properties += " PRIMARY KEY AUTOINCREMENT";
                return Optional.of(name + " " + _fromJavaTypeToDBType(propertyValueType) + properties);
            } else if ( this.kind == FieldKind.FOREIGN_KEY ) {
                String otherTable = _tableNameFromClass(propertyValueType);
                return Optional.of(name + " INTEGER REFERENCES " + otherTable + "(id)");
            } else if ( this.kind == FieldKind.INTERMEDIATE_TABLE ) {
                return Optional.empty(); // The field is not a column in the table, but a table itself
            } else
                throw new IllegalStateException("Unknown field kind: " + this.kind);
        }

        public Object getDefaultValue() {
            if ( this.kind == FieldKind.FOREIGN_KEY )
                return null;
            else if ( this.kind == FieldKind.INTERMEDIATE_TABLE )
                return null;
            else if ( this.kind == FieldKind.VALUE ) {
                if ( propertyValueType == String.class )
                    return "";
                else if ( propertyValueType == Integer.class )
                    return 0;
                else if ( propertyValueType == Double.class )
                    return 0.0;
                else if ( propertyValueType == Boolean.class )
                    return false;
                else
                    throw new IllegalStateException("Unknown property type: " + propertyValueType);
            } else if ( this.kind == FieldKind.ID ) {
                return 1;
            } else
                throw new IllegalStateException("Unknown field kind: " + this.kind);
        }

        public Vals<Object> asProperties(DataBase db, int id) {
            /*
                Now this is interesting.
                We have a list of properties represented in the form
                of an intermediate table.
                We know the name of the table, and we know the id of the
                model to which the table/model field belong.
                What we do not know is the ids of the models that are
                referenced by the intermediate table.
                So we need to query the table to find out.
            */
            ModelTable intermediateTable = getIntermediateTable().orElse(null);
            // We expect it to exist:
            if ( intermediateTable == null )
                throw new IllegalStateException("The intermediate table does not exist");

            // We need to find the name of the column that contains the ids of the models
            // that are referenced by the intermediate table:
            String otherTable = _tableNameFromClass(propertyValueType);
            String otherTableIdColumn = "fk_" + otherTable + "_id";
            String thisTableIdColumn = "fk_" + _tableNameFromClass(ownerModelClass) + "_id";
            String query = "SELECT " + otherTableIdColumn + " FROM " + intermediateTable.getName() + " WHERE " + thisTableIdColumn + " = ?";

            List<Object> param = Collections.singletonList(id);
            Map<String, List<Object>> result = db._query(query, param);
            // The result should contain a single column:
            if ( result.size() != 1 )
                throw new IllegalStateException("The result should contain a single column");
            // The column should be named after the id column of the other table:
            if ( !result.containsKey(otherTableIdColumn) )
                throw new IllegalStateException("The column should be named after the id column of the other table");
            // The column should contain a list of ids:
            List<Object> found = result.get(otherTableIdColumn);
            List<Integer> ids = new ArrayList<>(found.stream().map(o -> (Integer) o).toList());
            Vars<Object> vars = new Vars<>() {

                private Model<?> select(int id) {
                    // We need to get the model from the database:
                    Class<Model> propertyValueType = (Class<Model>) ModelField.this.propertyValueType;
                    Model<?> model = db.select(propertyValueType, id);
                    return model;
                }

                @Override
                public Iterator<Object> iterator() {
                    // We need to map the ids to the actual models:
                    return ids.stream().map(id -> {
                        // We need to get the model from the database:
                        return (Object) select(id);
                    }).iterator();
                }

                @Override
                public Class<Object> type() { return (Class<Object>) ModelField.this.propertyValueType; }

                @Override public int size() { return ids.size(); }

                @Override
                public Var<Object> at(int index) {
                    return new ModelProperty(
                            db,
                            ids.get(index),
                            "fk_" + otherTable + "_id",
                            intermediateTable.getName(),
                            propertyValueType
                        );
                }

                @Override
                public Vals<Object> onShow(Action<ValsDelegate<Object>> action) {
                    return null;
                }

                @Override
                public Vals<Object> show() {
                    throw new UnsupportedOperationException("Not supported yet.");
                }

                @Override
                public Vars<Object> removeAt(int index) {
                    /*
                        Basically all we need to do is delete a row from the intermediate table!
                        Which row? The one that contains the id of the left model and the id of the
                        model at the given index.
                    */
                    int leftId = id;
                    int rightId = ids.get(index);
                    String query = "DELETE FROM " + intermediateTable.getName() + " " +
                                    "WHERE " + thisTableIdColumn + " = ? AND " + otherTableIdColumn + " = ?";
                    List<Object> params = List.of(leftId, rightId);
                    db._update(query, params);
                    ids.remove(index);
                    return this;
                }

                @Override
                public Vars<Object> addAt(int index, Var<Object> var) {
                    /*
                        We need to insert a row into the intermediate table! Basic stuff...
                    */
                    int leftId = id;
                    int rightId = (Integer) var.get();
                    String query = "INSERT INTO " + intermediateTable.getName() + " " +
                                    "(" + thisTableIdColumn + ", " + otherTableIdColumn + ") " +
                                    "VALUES (?, ?)";
                    List<Object> params = List.of(leftId, rightId);
                    db._update(query, params);
                    ids.add(index, rightId);
                    return this;
                }

                @Override
                public Vars<Object> setAt(int index, Var<Object> var) {
                    /*
                        This is a bit more complicated.
                        We need to update the row in the intermediate table.
                        More specifically, we need to update the id of the model that is referenced
                        by the intermediate table.
                    */
                    int leftId = id;
                    int rightId = (Integer) var.get();
                    int oldRightId = ids.get(index);

                    String update = "UPDATE " + intermediateTable.getName() +
                                    " SET " + otherTableIdColumn +
                                    " = ? WHERE id = ?";

                    List<Object> params = List.of(rightId, oldRightId);
                    db._update(update, params);
                    ids.set(index, rightId);
                    return this;
                }

                @Override
                public Vars<Object> clear() {
                    /*
                        We need to delete all rows from the intermediate table that contain the id
                        of the left model.
                    */
                    String query = "DELETE FROM " + intermediateTable.getName() + " WHERE " + thisTableIdColumn + " = ?";
                    List<Object> params = List.of(id);
                    db._update(query, params);
                    ids.clear();
                    return this;
                }

                @Override
                public void sort(Comparator<Object> comparator) {
                    throw new UnsupportedOperationException("Not supported yet."); // How to sort on a database?
                }

                @Override
                public void makeDistinct() {
                    throw new UnsupportedOperationException("Not supported yet."); // How to make distinct on a database?
                }
            };

            // Let's create the proxy:
            return  (Vals<Object>) Proxy.newProxyInstance(
                    propertyType.getClassLoader(),
                    new Class[]{propertyType},
                    (proxy, method, args) -> {
                        // We simply delegate to the property
                        return method.invoke(vars, args);
                    }
            );
        }

    }

    private static class BasicModelTable implements ModelTable
    {
        private final ModelField[] fields;
        private final Class<? extends Model<?>> modelInterface;

        private BasicModelTable(Class<? extends Model<?>> modelInterface, List<Class<? extends Model<?>>> otherModels) {
            // We expect something like this:
            /*
                public interface Address extends Model<Address> {
                    interface Id extends Val<Integer> {} // Optional! But has to be immutable!
                    interface Street extends Var<String> {}
                    interface Number extends Var<Integer> {}
                    interface ZipCode extends Var<Integer> {}
                    interface City extends Var<String> {}
                    Street street();
                    Number number();
                    ZipCode zipCode();
                    City city();
                }
                // Note: Var is mutable and Val is immutable (only get but no set)
            */
            // First we need to check if the interface is a subclass of Model
            if ( !Model.class.isAssignableFrom(modelInterface) )
                throw new IllegalArgumentException(
                        "The interface " + modelInterface.getName() + " is not a subclass of " + Model.class.getName()
                    );
            // Now we check if the the first type parameter of the Model interface is the same as the interface itself
            // We need to find the extends clause of the interface
            if ( !Model.class.equals(modelInterface) ) {
                Type[] interfaces = modelInterface.getGenericInterfaces();
                if (interfaces.length != 1)
                    throw new IllegalArgumentException(
                            "The interface " + modelInterface.getName() + " has more than one extends clause"
                        );
                Type[] typeParameters = ((ParameterizedType) interfaces[0]).getActualTypeArguments();
                if (typeParameters.length != 1)
                    throw new IllegalArgumentException(
                            "The interface " + modelInterface.getName() + " is not a subclass of " + Model.class.getName() + " with one type parameter"
                    );

                if ( !modelInterface.equals(typeParameters[0]) )
                    throw new IllegalArgumentException(
                            "The first type parameter of the interface " + modelInterface.getName() + " is not the same as the interface itself"
                    );
            }
            // Now we can get the fields
            Method[] methods = modelInterface.getMethods();
            List<ModelField> fields = new ArrayList<>();
            for ( Method method : methods ) {
                Method[] allowedFields = Model.class.getMethods();
                if ( !Arrays.asList(allowedFields).contains(method) ) {
                    // We do not allow methods with parameters
                    if ( method.getParameterCount() != 0 ) {
                        throw new IllegalArgumentException(
                                "The method '" + method.getName() + "(..)' of the interface " + modelInterface.getName() + " has parameters" +
                                        "which is not allowed!"
                        );
                    }
                    // We do not allow methods with a return type of void
                    if (method.getReturnType().equals(Void.TYPE))
                        throw new IllegalArgumentException(
                                "The method " + method.getName() + " of the interface " + modelInterface.getName() + " has a return type of void"
                        );
                    // The method is not allowed to be called "id" because that is reserved for the id field:
                    if ( method.getDeclaringClass().equals(modelInterface) && method.getName().equals("id") )
                        throw new IllegalArgumentException(
                            "The method " + method.getName() + " of the interface " + modelInterface.getName() + " is not allowed to be called \"id\", " +
                            "because that is field is already present!"
                            );
                    fields.add(new ModelField(method, modelInterface, otherModels));
                }
            }
            // Now we add the id field
            Class<Model> modelClass = Model.class;
            try {
                Method idMethod = modelClass.getMethod("id");
                fields.add(0, new ModelField(idMethod, modelInterface, otherModels));
            } catch (NoSuchMethodException | SecurityException e) {
                throw new RuntimeException(e);
            }

            this.fields = fields.toArray(new ModelField[0]);
            this.modelInterface = modelInterface;
        }

        @Override
        public String getName() {
            return _tableNameFromClass(modelInterface);
        }

        @Override
        public List<ModelField> getFields() {
            return Arrays.asList(fields);
        }

        @Override
        public List<Class<? extends Model<?>>> getReferencedModels() {
            List<Class<? extends Model<?>>> referencedModels = new ArrayList<>();
            for ( ModelField field : fields ) {
                if ( field.isForeignKey() ) {
                    referencedModels.add((Class<? extends Model<?>>) field.getType());
                }
            }
            return referencedModels;
        }

        @Override
        public Optional<Class<? extends Model<?>>> getModelInterface() { return Optional.of(modelInterface); }

        @Override
        public String createTableStatement() {
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE TABLE IF NOT EXISTS ");
            sb.append(getName());
            sb.append(" (");
            for ( ModelField field : fields )
                field.asSqlColumn().ifPresent( col -> {
                    sb.append(col);
                    sb.append(", ");
                });

            sb.delete(sb.length() - 2, sb.length());
            sb.append(");");
            return sb.toString();
        }

        @Override
        public List<Object> getDefaultValues() {
            List<Object> defaultValues = new ArrayList<>();
            for ( ModelField field : fields ) {
                if ( field.isForeignKey() ) {
                    defaultValues.add(null);
                } else {
                    defaultValues.add(field.getDefaultValue());
                }
            }
            return defaultValues;
        }

    }

    interface ModelTable {

        String getName();

        List<ModelField> getFields();
        default ModelField getField(String name) {
            for ( ModelField field : getFields() ) {
                if ( field.isField(name) )
                    return field;
            }
            throw new IllegalArgumentException("No field with name " + name + " found!");
        }

        List<Class<? extends Model<?>>> getReferencedModels();

        default Optional<Class<? extends Model<?>>> getModelInterface() { return Optional.empty(); }

        String createTableStatement();

        List<Object> getDefaultValues();
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

            Map<Class<? extends Model<?>>, ModelTable> newModelTables = new LinkedHashMap<>();
            for ( Class<? extends Model<?>> modelInterface : modelInterfaces ) {
                ModelTable modelTable = new BasicModelTable(modelInterface, modelInterfaces);
                newModelTables.put(modelInterface, modelTable);
                modelTable.getFields().forEach(
                        f -> f.getIntermediateTable().ifPresent(
                                t -> newModelTables.put((Class<? extends Model<?>>) f.getType(), t)
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
                ModelTable modelTable = newModelTables.get(model);
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
                Map<Class<? extends Model<?>>, ModelTable> newModelTables
        ) {
            if ( visited.contains(modelTable) )
                return false;
            if ( currentPath.contains(modelTable) )
                return true;
            currentPath.add(modelTable);
            for ( Class<? extends Model<?>> referencedModel : modelTable.getReferencedModels() ) {
                if ( _hasCycle(newModelTables.get(referencedModel), visited, currentPath, newModelTables) )
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



}
