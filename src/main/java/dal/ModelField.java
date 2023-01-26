package dal;

import dal.api.Model;
import swingtree.api.mvvm.*;

import java.lang.reflect.*;
import java.util.*;

class ModelField {

    private final Method method;
    private final Class<? extends Model<?>> ownerModelClass;
    private final Class<?> propertyValueType;
    private final Class<?> propertyType;
    private final FieldKind kind;
    private final List<Class<? extends Model<?>>> otherModels;

    ModelField(Method method, Class<? extends Model<?>> ownerClass, List<Class<? extends Model<?>>> otherModels) {
        this.method = method;
        this.ownerModelClass = ownerClass;
        this.propertyType = method.getReturnType();
        this.otherModels = Collections.unmodifiableList(otherModels);

        // First we check if the return type is a subclass of Val or Vals
        boolean isVal = Val.class.isAssignableFrom(propertyType);
        boolean isVals = Vals.class.isAssignableFrom(propertyType);

        if (!isVal && !isVals)
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
        if (!declaringClass.equals(outerClassOfNestedClass))
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
        if (typeParameters.length != 0)
            throw new IllegalArgumentException(
                    "The return type of the method " + method.getName() + " may not have generic parameters!"
            );
        Type[] genericInterfaces = propertyType.getGenericInterfaces();
        if (genericInterfaces.length != 1)
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
        if (method.getName().equals("id")) {
            if (!propertyType.equals(Model.Id.class))
                throw new IllegalArgumentException(
                        "The return type of the method " + method.getName() + " is not " + Model.Id.class.getName()
                );
            kind = FieldKind.ID;
        }
        // Then we check if the field is a foreign key field
        else if (isVal) {
            if (Model.class.isAssignableFrom(propertyValueType)) {
                if (otherModels.contains(propertyValueType)) {
                    kind = FieldKind.FOREIGN_KEY;
                } else
                    throw new IllegalArgumentException(
                            "The return type of the method " + method.getName() + " is a foreign key to a model " +
                                    "however the provided model type '" + propertyValueType.getName() + "' is not known " +
                                    "by the database!"
                    );
            } else if (AbstractDataBase._isBasicDataType(propertyValueType)) {
                kind = FieldKind.VALUE;
            } else
                throw new IllegalArgumentException(
                        "The return type of the method " + method.getName() + " is not a basic data type " +
                                "and is not a foreign key to a model!"
                );
        }
        // Then we check if the field is an intermediate table field
        else if (isVals) {
            if (otherModels.contains(propertyValueType)) {
                kind = FieldKind.INTERMEDIATE_TABLE;
            } else {
                if (AbstractDataBase._isBasicDataType(propertyValueType))
                    throw new IllegalArgumentException(
                            "List of basic data types cannot be modelled as table fields."
                    );
                else
                    throw new IllegalArgumentException(
                            "The type '" + propertyType.getName() + "' of the property returned by " +
                                    "method " + method.getName() + " is not a known model type."
                    );
            }
        } else
            throw new IllegalArgumentException(
                    "The return type of the method " + method.getName() + " is not a subclass " +
                            "of " + Val.class.getName() + " or " + Vals.class.getName() + " with one type parameter"
            );


    }

    public String getName() {
        if (this.kind == FieldKind.FOREIGN_KEY)
            return "fk_" + method.getName() + "_id";
        return method.getName();
    }

    public boolean isField(String name) {
        return method.getName().equals(name);
    }

    public Class<?> getType() {
        return propertyValueType;
    }

    public Class<?> getPropType() {
        return propertyType;
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
        return getName() + " " + AbstractDataBase._fromJavaTypeToDBType(propertyValueType);
    }

    public Optional<ModelTable> getIntermediateTable() {
        if (requiresIntermediateTable())
            return Optional.of(new ModelTable() {
                @Override
                public String getName() {
                    return ModelField.this.getName() + "_list_table";
                }

                @Override
                public List<ModelField> getFields() {
                    return Collections.emptyList();
                }

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
                    String thisTable = AbstractDataBase._tableNameFromClass(thisTableClass);
                    String otherTable = AbstractDataBase._tableNameFromClass(otherTableClass);
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

                @Override
                public boolean isIntermediateTable() {
                    return true;
                }

            });
        else
            return Optional.empty();
    }

    public Val<Object> asProperty(DataBase db, int id) {
        var prop = new ModelProperty(db, id, this.getName(), AbstractDataBase._tableNameFromClass(ownerModelClass), propertyValueType);

        Class<?> propertyType = ModelField.this.propertyType;

        // Let's check if the property is a Val
        boolean isVal = Val.class.isAssignableFrom(propertyType);
        if (!isVal)
            throw new IllegalArgumentException(
                    "The return type of the method " + method.getName() + " is not a subclass " +
                            "of " + Val.class.getName() + " or " + Vals.class.getName() + " with one type parameter"
            );

        // Let's create the proxy:
        return (Val<Object>) Proxy.newProxyInstance(
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
        if (!Model.class.isAssignableFrom(propertyValueType)) {
            String properties = " NOT NULL";
            if (name.equals("id"))
                properties += " PRIMARY KEY AUTOINCREMENT";
            return Optional.of(name + " " + AbstractDataBase._fromJavaTypeToDBType(propertyValueType) + properties);
        } else if (this.kind == FieldKind.FOREIGN_KEY) {
            String otherTable = AbstractDataBase._tableNameFromClass(propertyValueType);
            return Optional.of(name + " INTEGER REFERENCES " + otherTable + "(id)");
        } else if (this.kind == FieldKind.INTERMEDIATE_TABLE) {
            return Optional.empty(); // The field is not a column in the table, but a table itself
        } else
            throw new IllegalStateException("Unknown field kind: " + this.kind);
    }

    public Object getDefaultValue() {
        if (this.kind == FieldKind.FOREIGN_KEY)
            return null;
        else if (this.kind == FieldKind.INTERMEDIATE_TABLE)
            return null;
        else if (this.kind == FieldKind.VALUE) {
            if (propertyValueType == String.class)
                return "";
            else if (propertyValueType == Integer.class)
                return 0;
            else if (propertyValueType == Double.class)
                return 0.0;
            else if (propertyValueType == Boolean.class)
                return false;
            else
                throw new IllegalStateException("Unknown property type: " + propertyValueType);
        } else if (this.kind == FieldKind.ID) {
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
        if (intermediateTable == null)
            throw new IllegalStateException("The intermediate table does not exist");

        // We need to find the name of the column that contains the ids of the models
        // that are referenced by the intermediate table:
        String otherTable = AbstractDataBase._tableNameFromClass(propertyValueType);
        String otherTableIdColumn = "fk_" + otherTable + "_id";
        String thisTableIdColumn = "fk_" + AbstractDataBase._tableNameFromClass(ownerModelClass) + "_id";
        String query = "SELECT " + otherTableIdColumn + " FROM " + intermediateTable.getName() + " WHERE " + thisTableIdColumn + " = ?";

        List<Object> param = Collections.singletonList(id);
        Map<String, List<Object>> result = db._query(query, param);
        // The result should contain a single column:
        //if ( result.size() != 1 )
        //    throw new IllegalStateException("The result should contain a single column");

        if (result.size() == 0)
            result.put(otherTableIdColumn, new ArrayList<>());

        // The column should be named after the id column of the other table:
        if (!result.containsKey(otherTableIdColumn))
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
            public Class<Object> type() {
                return (Class<Object>) ModelField.this.propertyValueType;
            }

            @Override
            public int size() {
                return ids.size();
            }

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
                // First let's verify the type:
                if (!propertyValueType.isAssignableFrom(var.type()))
                    throw new IllegalArgumentException("The type of the var is not the same as the type of the property");

                /*
                    We need to insert a row into the intermediate table! Basic stuff...
                */
                Object o = var.get();
                int leftId = id;
                int rightId = ((Model) o).id().get();
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
        return (Vals<Object>) Proxy.newProxyInstance(
                propertyType.getClassLoader(),
                new Class[]{propertyType},
                (proxy, method, args) -> {
                    // We simply delegate to the property
                    return method.invoke(vars, args);
                }
        );
    }

}
