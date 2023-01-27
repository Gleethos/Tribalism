package dal.impl;

import dal.api.Model;
import swingtree.api.mvvm.*;

import java.lang.reflect.*;
import java.util.*;

final class TableField {

    private final Method method; // The method from the model class
    private final Class<? extends Model<?>> ownerModelClass; // The model class
    private final Class<?> propertyType; // The type of the property and return type of the method
    private final Class<?> propertyValueType; // The type of the property value
    private final FieldKind kind;

    TableField(Method method, Class<? extends Model<?>> ownerClass, List<Class<? extends Model<?>>> otherModels) {
        this.method = method;
        this.ownerModelClass = ownerClass;
        this.propertyType = method.getReturnType();

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
        if (!declaringClass.equals(outerClassOfNestedClass)) {
            if ( propertyType == Var.class || propertyType == Vars.class || propertyType == Val.class || propertyType == Vals.class )
                throw new IllegalArgumentException(
                        "Model '" + this.ownerModelClass.getName() + "' is invalid because the return type " +
                        "of the method " + method.getName() + " is the generic property type " + propertyType.getName() + " " +
                        "instead of a locally defined custom property type.\n" +
                        "Please define a local property type inside '" + this.ownerModelClass.getName() + "' similar to " +
                        "'interface MyProp extends "+propertyType.getSimpleName()+"<MyValue> {}' " +
                        "and return that instead: 'public MyProp " + method.getName() + "();'.\n" +
                        "This is important to allow for compile time query building on the database API."
                    );
            throw new IllegalArgumentException(
                    "Model '" + this.ownerModelClass.getName() + "' is invalid because return " +
                    "type '" + propertyType.getName() + "' of " +
                    "method '" + method.getName() + "' is not declared as an " +
                    "inner interface of the provided interface '" + declaringClass.getName() + "'." +
                    "Please define a local property type inside '" + this.ownerModelClass.getName() + "' similar to " +
                    "'interface MyProp extends "+propertyType.getSimpleName()+"<MyValue> {}' " +
                    "and return that instead: 'public MyProp " + method.getName() + "();'.\n" +
                    "This is important to allow for compile time query building on the database API."
                );
        }

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
                public String getTableName() {
                    return TableField.this.getName() + "_list_table";
                }

                @Override
                public List<TableField> getFields() {
                    return Collections.emptyList();
                }

                @Override
                public List<Class<? extends Model<?>>> getReferencedModels() {
                    Class<?> thisTableClass = TableField.this.method.getDeclaringClass();
                    Class<?> otherTableClass = TableField.this.propertyValueType;
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
                    Class<?> thisTableClass = TableField.this.method.getDeclaringClass();
                    Class<?> otherTableClass = TableField.this.propertyValueType;
                    String thisTable = AbstractDataBase._tableNameFromClass(thisTableClass);
                    String otherTable = AbstractDataBase._tableNameFromClass(otherTableClass);
                    return "CREATE TABLE " + getTableName() + " (\n" +
                            "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                            "    fk_self_" + thisTable + "_id INTEGER NOT NULL,\n" +
                            "    fk_" + otherTable + "_id INTEGER NOT NULL,\n" +
                            "    FOREIGN KEY (fk_self_" + thisTable + "_id) REFERENCES " + thisTable + "(id),\n" +
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

    public Val<Object> asProperty(SQLiteDataBase db, int id) {
        var prop = new ModelProperty(db, id, this.getName(), AbstractDataBase._tableNameFromClass(ownerModelClass), propertyValueType);

        Class<?> propertyType = TableField.this.propertyType;

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

    public Vals<Object> asProperties(SQLiteDataBase db, int id) {
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


        Vars<Object> vars = new ModelProperties(db, this.ownerModelClass, this.propertyValueType, intermediateTable, id);

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
