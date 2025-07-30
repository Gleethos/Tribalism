package dal.impl;

import dal.api.Model;
import sprouts.*;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

record TableField(
    Method method, // The method from the model class
    Class<? extends Model<?>> ownerModelClass, // The model class
    Class<?> propertyType, // The type of the property and return type of the method
    Class<?> propertyValueType, // The type of the property value
    FieldKind kind,
    boolean allowNull
) {


    public static TableField of(
        final Method method, // The method from the model class
        final Class<? extends Model<?>> ownerModelClass, // The model class
        final Tuple<Class<? extends Model<?>>> otherModels
    ) {
        final Class<?> propertyType = method.getReturnType(); // The type of the property and return type of the method
        Class<?> propertyValueType; // The type of the property value
        FieldKind kind;
        boolean allowNull;

        // First we check if the return type is a subclass of Val or Vals
        boolean isSubTypeOfVal  = Val.class.isAssignableFrom(propertyType);
        boolean isSubTypeOfVals = Vals.class.isAssignableFrom(propertyType);
        boolean isValOrVar   = propertyType == Val.class  || propertyType == Var.class;
        boolean isValsOrVars = propertyType == Vals.class || propertyType == Vars.class;

        if ( !isSubTypeOfVal && !isSubTypeOfVals )
            throw new IllegalArgumentException(
                    "The return type of method '" + method.getName() + "' " +
                    "in model type '" + ownerModelClass.getName() + "' " +
                    "is not a subclass of " +
                    "either " + Val.class.getName() + " or " + Vals.class.getName() + ". \n" +
                    "You might want to declare the method as a default method in the model interface " +
                    "or wrap the return type in a property type, like so: \n" +
                    "'public " + Var.class.getSimpleName() + "<" + method.getReturnType().getSimpleName() + "> " + method.getName() + "()'"
                );

        // Great that is correct! But now we have another requirement:
        /*
            So a model interface might look something like this:
            public interface Person extends Model<Person> {
                interface Name extends Var<String> {} // Val is a property type with getter and setter
                interface Addresses extends Vars<Address> {} // Vars is a property type with getter and setter wrapping multiple values
                Name name();
                Var<Integer> age(); // Not declared as an inner interface of the model interface, which is okay
            }
            We expect that the return type of the method is either equal to or a subclass of Val/Vals...
            ...if it is a subclass we also expect it to be declared as an inner interface of the model interface!
            This is because of readability and coherence and also
            because we might need to be able to get the name of the field from the interface in the query API!

            The main goal here is to get the type of the value of the property
            This is a generic type parameter of the Val interface
            So in the example below for "Name" it would be String
            and for method "age" it would be Integer:

            public interface Person extends Model<Person> {
                interface Name extends Val<String> {} // Val is a property type with getter and setter
                Name name();
                Var<Integer> age(); // Not declared as an inner interface of the model interface, which is okay
            }
         */
        if ( !isValOrVar && !isValsOrVars ) {
            // It is a subclass of Val or Vals:
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
        } else {
            // The return type is Val<T>, Var<T>, Vals<T> or Vars<T> so we can get the type parameter T easily:
            // However we can not get the declared type from Var,Val... it is a generic type...
            // Instead, we get the type from the method parameter
            var declaredReturnTypeGenericParam = method.getGenericReturnType();
            if ( declaredReturnTypeGenericParam instanceof ParameterizedType ) {
                var declaredReturnTypeGenericParamType = ((ParameterizedType) declaredReturnTypeGenericParam).getActualTypeArguments()[0];
                if ( declaredReturnTypeGenericParamType instanceof Class<?> ) {
                    propertyValueType = (Class<?>) declaredReturnTypeGenericParamType;
                } else {
                    throw new IllegalArgumentException(
                            "The return type of the method " + method.getName() + " must be a class!"
                        );
                }
            } else {
                throw new IllegalArgumentException(
                        "The return type of the method " + method.getName() + " must be a parameterized type!"
                    );
            }
        }
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
        if (method.getName().equals(ModelTable.ID)) {
            if (!propertyType.equals(Model.Id.class))
                throw new IllegalArgumentException(
                    "The return type of the method " + method.getName() + " is not " + Model.Id.class.getName()
                );
            kind = FieldKind.ID;
        }
        // Then we check if the field is a foreign key field
        else if ( isSubTypeOfVal ) {
            if (Model.class.isAssignableFrom(propertyValueType)) {
                if (otherModels.contains((Class<? extends Model<?>>) propertyValueType)) {
                    kind = FieldKind.FOREIGN_KEY;
                } else
                    throw new IllegalArgumentException(
                        "Cannot establish table field for method '" + method.getName() + "()' for model '" + ownerModelClass.getName() + "', \n" +
                        "because the return type of " +
                        "the method is a property referencing another model called '" + propertyValueType.getName() + "', " +
                        "which is however not known " +
                        "by the database, please make sure that it is passed to the 'createTablesFor(..)' method alongside " +
                        "all other model types!"
                    );
            } else if (AbstractDataBase._isBasicDataType(propertyValueType)) {
                kind = FieldKind.PRIMITIVE;
            } else {
                boolean propertyValueIsModel = Model.class.isAssignableFrom(propertyValueType);
                if ( !propertyValueIsModel )
                    throw new IllegalArgumentException(
                            "Failed to create table field '" + method.getName() + "' for model '" + ownerModelClass.getName() + "', because \n" +
                            "the property value type '" + propertyValueType.getName() + "' in declared method " +
                            "'public " + propertyType.getSimpleName() + "<" + propertyValueType.getSimpleName() + "> " + method.getName() + "();' " +
                            "is not a basic data type and is also not recognisable as another model! \n" +
                            "If you want this declaration to work, make sure that '" + propertyValueType.getName() + "' is a subtype of the '" + Model.class.getName() + "' interface " +
                            "and also is passed to the the 'createTablesFor(Class<M>... models);' method."
                        );
                else // The user has simply not passed the interface class to the createTablesFor(Class<Model... models) method:
                    throw new IllegalArgumentException(
                            "Failed to create table field '" + method.getName() + "' for model '" + ownerModelClass.getName() + "', because \n" +
                            "the property value type '" + propertyValueType.getName() + "' in declared method " +
                            "'public " + propertyType.getSimpleName() + "<" + propertyValueType.getSimpleName() + "> " + method.getName() + "();' " +
                            "is a model type not known to the database! " +
                            "If you want this declaration to work, make sure that you have passed the interface class of the model to the " +
                            "createTablesFor(Class<M>... models); method!"
                        );
            }
        }
        // Then we check if the field is an intermediate table field
        else if (isSubTypeOfVals) {
            if (otherModels.contains((Class<? extends Model<?>>) propertyValueType)) {
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

        allowNull = Model.class.isAssignableFrom(propertyValueType);
        return new TableField(
                method,
                ownerModelClass,
                propertyType,
                propertyValueType,
                kind,
                allowNull
            );
    }

    public String getName() {
        if ( kind == FieldKind.FOREIGN_KEY )
            return ModelTable.FK_PREFIX + method.getName() + ModelTable.FK_POSTFIX;
        return method.getName();
    }

    public String getMethodName() {
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
                    return AbstractDataBase._nameFromClass(ownerModelClass) + "__" + TableField.this.getName() + INTER_TABLE_POSTFIX;
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
                            "    " + ModelTable.INTER_LEFT_FK_PREFIX + thisTable + ModelTable.INTER_FK_POSTFIX + " INTEGER NOT NULL,\n" +
                            "    " + ModelTable.INTER_RIGHT_FK_PREFIX + otherTable + ModelTable.INTER_FK_POSTFIX + " INTEGER NOT NULL,\n" +
                            "    FOREIGN KEY (" + ModelTable.INTER_LEFT_FK_PREFIX + thisTable + ModelTable.INTER_FK_POSTFIX + ") REFERENCES " + thisTable + "(id),\n" +
                            "    FOREIGN KEY (" + ModelTable.INTER_RIGHT_FK_PREFIX + otherTable + ModelTable.INTER_FK_POSTFIX + ") REFERENCES " + otherTable + "(id)\n" +
                            ");";
                }

                @Override
                public List<Object> getDefaultValues() {
                    throw new UnsupportedOperationException("An intermediate table does not have default values");
                }

            });
        else
            return Optional.empty();
    }

    public ProxyRef<Val<Object>> asProperty(SQLiteDataBase db, int id, boolean eager ) {
        var prop = new ModelProperty(
                        db, id, this.getName(),
                        AbstractDataBase._tableNameFromClass(ownerModelClass),
                propertyValueType,
                allowNull,
                        eager
                    );

        // Let's check if the property is a Val
        boolean isVal = Val.class.isAssignableFrom(propertyType);
        if (!isVal)
            throw new IllegalArgumentException(
                    "The return type of the method " + method.getName() + " is not a subclass " +
                            "of " + Val.class.getName() + " or " + Vals.class.getName() + " with one type parameter"
            );

        // Let's create the proxy:
        return new ProxyRef<>((Val<Object>) Proxy.newProxyInstance(
                        propertyType.getClassLoader(),
                        new Class[]{propertyType, Viewable.class},
                        (proxy, method, args) -> {
                            return _handleInvocation(proxy, method, args, prop, propertyType);
                        }
                    ),
                    prop
                );
    }

    private Object _handleInvocation(
        Object proxy,
        Method method,
        Object[] args,
        Object prop,
        Class<?> propertyType
    ) throws InvocationTargetException, IllegalAccessException {
        String methodName = method.getName();
        // Check if it is 'toString()':
        if ( methodName.equals("toString") && (args == null || args.length == 0) ) {
            return prop.toString();
        }
        try {
            Method proxyTypeMethod = propertyType.getMethod(methodName, method.getParameterTypes());
            // Then we expect the method to be a default method
            if (proxyTypeMethod.isDefault()) {
                // A default method is a method that is defined in an interface, we can just call it
                return MethodHandles.lookup()
                        .findSpecial(
                                propertyType,
                                methodName,
                                MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
                                propertyType
                        )
                        .bindTo(proxy)
                        .invokeWithArguments(args);
            }
        } catch (Exception e) {
            // If we get here, it means that the method is not a default method
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        try {
            // Here we delegate to the property
            return method.invoke(prop, args);
            /*
                The method here might be one of the following:
                - get()
                - orElseNull()
                - set(T value)
                - isEmpty()
                - ... see Val & Var interfaces for more ...
            */
        } catch (InvocationTargetException e) {
            // We don't really care about the InvocationTargetException, we just want to throw the cause exception:
            if (e.getCause() instanceof RuntimeException)
                throw (RuntimeException) e.getCause();
            else
                throw new RuntimeException(e.getCause());
        }
    }

    public Optional<String> asSqlColumn() {
        String name = getName();
        if (!Model.class.isAssignableFrom(propertyValueType)) {
            String properties = allowNull ? "" : " NOT NULL";
            if (name.equals(ModelTable.ID))
                properties += " PRIMARY KEY AUTOINCREMENT";
            return Optional.of(name + " " + AbstractDataBase._fromJavaTypeToDBType(propertyValueType) + properties);
        } else if ( kind == FieldKind.FOREIGN_KEY) {
            String otherTable = AbstractDataBase._tableNameFromClass(propertyValueType);
            return Optional.of(name + " INTEGER REFERENCES " + otherTable + "("+ ModelTable.ID+")");
        } else if ( kind == FieldKind.INTERMEDIATE_TABLE) {
            return Optional.empty(); // The field is not a column in the table, but a table itself
        } else
            throw new IllegalStateException("Unknown field kind: " + kind);
    }

    public Object getDefaultValue() {
        if ( kind == FieldKind.FOREIGN_KEY )
            return null;
        else if ( kind == FieldKind.INTERMEDIATE_TABLE )
            return null;
        else if ( kind == FieldKind.PRIMITIVE) {
            if ( propertyValueType == String.class )
                return "";
            else if ( propertyValueType == Integer.class )
                return 0;
            else if ( propertyValueType == Double.class )
                return 0.0;
            else if ( propertyValueType == Boolean.class )
                return false;
            else if ( propertyValueType == Long.class )
                return 0L;
            else if ( propertyValueType == Float.class )
                return 0.0f;
            else if ( propertyValueType == Short.class )
                return (short) 0;
            else if ( propertyValueType == Byte.class )
                return (byte) 0;
            else if ( Enum.class.isAssignableFrom(propertyValueType) )
                return propertyValueType.getEnumConstants()[0];
            else
                throw new IllegalStateException( "Unknown property type: " + propertyValueType);
        } else if ( kind == FieldKind.ID ) {
            return 1;
        } else
            throw new IllegalStateException("Unknown field kind: " + kind);
    }

    public ProxyRef<Vals<Object>> asProperties( SQLiteDataBase db, int id, boolean eager ) {
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


        Vars<Object> vars = new ModelProperties(db, ownerModelClass, propertyValueType, intermediateTable, id, eager);

        // Let's create the proxy:
        return new ProxyRef<>((Vals<Object>) Proxy.newProxyInstance(
                        propertyType.getClassLoader(),
                        new Class[]{propertyType, Viewables.class},
                        (proxy, method, args) -> {
                            return _handleInvocation(proxy, method, args, vars, propertyType);
                        }
                    ),
                    vars
                );
    }

    @Override public String toString() {
        return "TableField[" + "name=" + getName() + ", type=" + propertyValueType + ", kind=" + kind + ']';
    }

}
