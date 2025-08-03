package dal.impl;

import dal.api.DataBaseEntity;
import dal.api.Model;
import dal.api.Value;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import sprouts.*;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.Optional;

@NullMarked
record EntityTableField(
    String baseName, // The method baseName from the model class
    Class<? extends DataBaseEntity> ownerModelClass, // The model class
    @Nullable Class<?> wrapperType, // The type of the property and return type of the method
    Class<?> itemType, // The type of the property value
    FieldKind kind,
    boolean allowNull
) {
    sealed interface Params {
        Class<?> type();
        record Single(Class<?> type) implements Params {}
        record TupleOf(Class<?> type) implements Params {}
    }

    public static EntityTableField ofValue(
            final Method method, // The method from the model class
            final Class<? extends Value> ownerEntityClass, // The model class
            final Tuple<Class<? extends DataBaseEntity>> otherEntities
    ) {
        final String methodName = method.getName();
        final Class<?> methodReturnType = method.getReturnType(); // The type of the property and return type of the method
        final Type declaredReturnTypeGenericParam = method.getGenericReturnType();
        boolean isTuple = Tuple.class.isAssignableFrom(methodReturnType);
        boolean isPrimitive = AbstractDataBase._isBasicDataType(methodReturnType);
        boolean isValue = Value.class.isAssignableFrom(methodReturnType);
        if ( Value.class.isAssignableFrom(ownerEntityClass) ) {
            if ( !isTuple && !isPrimitive && !isValue ) {
                throw new IllegalArgumentException(
                        "The return type of " + methodName + "() must be a tuple, primitive or Value"
                );
            }
        }
        var params = extractParamsFrom(declaredReturnTypeGenericParam, methodName);
        FieldKind kind;
        if ( isTuple )
            kind = FieldKind.INTERMEDIATE_TABLE;
        else if ( isPrimitive )
            kind = FieldKind.PRIMITIVE;
        else
            kind = FieldKind.FOREIGN_KEY;
        return new EntityTableField(
                methodName,
                ownerEntityClass,
                isTuple ? Tuple.class : null,
                params.type(),
                kind,
                false
        );
    }

    public static EntityTableField of(
        final Method method, // The method from the model class
        final Class<? extends DataBaseEntity> ownerEntityClass, // The model class
        final Tuple<Class<? extends DataBaseEntity>> otherEntities
    ) {
        final String methodName = method.getName();
        final Class<?> methodReturnType = method.getReturnType(); // The type of the property and return type of the method
        final Type declaredReturnTypeGenericParam = method.getGenericReturnType();
        Params propertyParams = null; // The type of the property value
        FieldKind kind;
        boolean allowNull;

        // First we check if the return type is a subclass of Val or Vals
        boolean isSubTypeOfVal  = Val.class.isAssignableFrom(methodReturnType);
        boolean isSubTypeOfVals = Vals.class.isAssignableFrom(methodReturnType);
        boolean isValOrVar   = methodReturnType == Val.class  || methodReturnType == Var.class;
        boolean isValsOrVars = methodReturnType == Vals.class || methodReturnType == Vars.class;

        if ( Model.class.isAssignableFrom(ownerEntityClass) ) {
            if ( !isSubTypeOfVal && !isSubTypeOfVals )
                throw new IllegalArgumentException(
                        "The return type of method '" + methodName + "' " +
                        "in model type '" + ownerEntityClass.getName() + "' " +
                        "is not a subclass of " +
                        "either " + Val.class.getName() + " or " + Vals.class.getName() + ". \n" +
                        "You might want to declare the method as a default method in the model interface " +
                        "or wrap the return type in a property type, like so: \n" +
                        "'public " + Var.class.getSimpleName() + "<" + methodReturnType.getSimpleName() + "> " + methodName + "()'"
                    );
        } else if ( Value.class.isAssignableFrom(ownerEntityClass) ) {
            throw new IllegalStateException("Unexpected factory method argument.");
        }

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
            TypeVariable<?>[] typeParameters = methodReturnType.getTypeParameters();
            if (typeParameters.length != 0)
                throw new IllegalArgumentException(
                        "The return type of the method " + methodName + " may not have generic parameters!"
                );
            Type[] genericInterfaces = methodReturnType.getGenericInterfaces();
            if (genericInterfaces.length != 1)
                throw new IllegalArgumentException(
                        "The return type of the method " + methodName + " must implement exactly one interface!"
                );

            Type genericInterface = genericInterfaces[0];
            Type[] actualTypeArguments = ((ParameterizedType) genericInterface).getActualTypeArguments();
            propertyParams = extractParamsFrom(actualTypeArguments[0], methodName);
        } else {
            // The return type is Val<T>, Var<T>, Vals<T> or Vars<T> so we can get the type parameter T easily:
            // However we can not get the declared type from Var,Val... it is a generic type...
            // Instead, we get the type from the method parameter
            propertyParams = extractParamsFrom(declaredReturnTypeGenericParam, methodName);
        }
        // Now we need to determine the kind of the field, here are the possibilities:
        /*
            public interface Person extends Model<Person> {
                interface Address extends Var<Address> {}  // Kind: FOREIGN_KEY
                interface Name extends Var<String> {}      // Kind: PRIMITIVE
                interface Age extends Var<Integer> {}      // Kind: PRIMITIVE
                interface Children extends Vars<Person> {} // Kind: INTERMEDIATE_TABLE
            }
            // ... and ...
            public interface Model<M> {
                interface Id extends Val<Integer> {} // Kind: ID
                Id id();
                ...
            }
         */

        // First, we check if the field is an ID field
        if (methodName.equals(EntityTable.ID)) {
            if (!methodReturnType.equals(Model.Id.class))
                throw new IllegalArgumentException(
                    "The return type of the method " + methodName + " is not " + Model.Id.class.getName()
                );
            kind = FieldKind.ID;
        }
        // Then we check if the field is a foreign key field
        else if ( isSubTypeOfVal ) {
            if ( propertyParams instanceof Params.TupleOf && Value.class.isAssignableFrom(propertyParams.type()) ) {
                if (otherEntities.contains((Class<? extends Value>) propertyParams.type())) {
                    kind = FieldKind.INTERMEDIATE_TABLE;
                } else {
                    if (AbstractDataBase._isBasicDataType(propertyParams.type()))
                        throw new IllegalArgumentException(
                                "List of basic data types cannot be modelled as table fields."
                        );
                    else
                        throw new IllegalArgumentException(
                            "Cannot establish table field for method '" + methodName + "()' for value type '" + ownerEntityClass.getName() + "', \n" +
                            "because the return type of the method is a property referencing a tuple of values " +
                            "with type '" + propertyParams.type().getName() + "', which is however not known " +
                            "by the database, please make sure that it is passed to the 'createTablesFor(..)' method alongside " +
                            "all other value and model types!"
                        );
                }
            } else if (Value.class.isAssignableFrom(propertyParams.type())) {
                if (otherEntities.contains((Class<? extends Value>) propertyParams.type())) {
                    kind = FieldKind.FOREIGN_KEY;
                } else
                    throw new IllegalArgumentException(
                        "Cannot establish table field for method '" + methodName + "()' for value type '" + ownerEntityClass.getName() + "', \n" +
                        "because the return type of the method is a property referencing another value " +
                        "called '" + propertyParams.type().getName() + "', which is however not known " +
                        "by the database, please make sure that it is passed to the 'createTablesFor(..)' method alongside " +
                        "all other value and model types!"
                    );
            } else if (Model.class.isAssignableFrom(propertyParams.type())) {
                if (otherEntities.contains((Class<? extends Model<?>>) propertyParams.type())) {
                    kind = FieldKind.FOREIGN_KEY;
                } else
                    throw new IllegalArgumentException(
                        "Cannot establish table field for method '" + methodName + "()' for model '" + ownerEntityClass.getName() + "', \n" +
                        "because the return type of " +
                        "the method is a property referencing another model called '" + propertyParams.type().getName() + "', " +
                        "which is however not known " +
                        "by the database, please make sure that it is passed to the 'createTablesFor(..)' method alongside " +
                        "all other model types!"
                    );
            } else if (AbstractDataBase._isBasicDataType(propertyParams.type())) {
                kind = FieldKind.PRIMITIVE;
            } else {
                boolean propertyValueIsModel = Model.class.isAssignableFrom(propertyParams.type());
                if ( !propertyValueIsModel )
                    throw new IllegalArgumentException(
                            "Failed to create table field '" + methodName + "' for model '" + ownerEntityClass.getName() + "', because \n" +
                            "the property value type '" + propertyParams.type().getName() + "' in declared method " +
                            "'public " + methodReturnType.getSimpleName() + "<" + propertyParams.type().getSimpleName() + "> " + methodName + "();' " +
                            "is not a basic data type and is also not recognisable as another model! \n" +
                            "If you want this declaration to work, make sure that '" + propertyParams.type().getName() + "' is a subtype of the '" + Model.class.getName() + "' interface " +
                            "and also is passed to the the 'createTablesFor(Class<M>... models);' method."
                        );
                else // The user has simply not passed the interface class to the createTablesFor(Class<Model... models) method:
                    throw new IllegalArgumentException(
                            "Failed to create table field '" + methodName + "' for model '" + ownerEntityClass.getName() + "', because \n" +
                            "the property value type '" + propertyParams.type().getName() + "' in declared method " +
                            "'public " + methodReturnType.getSimpleName() + "<" + propertyParams.type().getSimpleName() + "> " + methodName + "();' " +
                            "is a model type not known to the database! " +
                            "If you want this declaration to work, make sure that you have passed the interface class of the model to the " +
                            "createTablesFor(Class<M>... models); method!"
                        );
            }
        }
        // Then we check if the field is an intermediate table field
        else if (isSubTypeOfVals) {
            if (otherEntities.contains((Class<? extends Model<?>>) propertyParams.type())) {
                kind = FieldKind.INTERMEDIATE_TABLE;
            } else {
                if (AbstractDataBase._isBasicDataType(propertyParams.type()))
                    throw new IllegalArgumentException(
                            "List of basic data types cannot be modelled as table fields."
                    );
                else
                    throw new IllegalArgumentException(
                            "The type '" + methodReturnType.getName() + "' of the property returned by " +
                                    "method " + methodName + " is not a known model type."
                    );
            }
        } else
            throw new IllegalArgumentException(
                    "The return type of the method " + methodName + " is not a subclass " +
                            "of " + Val.class.getName() + " or " + Vals.class.getName() + " with one type parameter"
            );

        allowNull = Model.class.isAssignableFrom(propertyParams.type());
        return new EntityTableField(
                methodName,
                ownerEntityClass,
                methodReturnType,
                propertyParams.type(),
                kind,
                allowNull
            );
    }

    private static Params extractParamsFrom(Type type, String methodName) {
        if ( type instanceof ParameterizedType ) {
            if ( !Tuple.class.equals(((ParameterizedType) type).getRawType()) ) {
                var declaredReturnTypeGenericParamType = ((ParameterizedType) type).getActualTypeArguments()[0];
                if ( declaredReturnTypeGenericParamType instanceof Class<?> ) {
                    return new Params.Single((Class<?>) declaredReturnTypeGenericParamType);
                } else {
                    throw new IllegalArgumentException(
                      "The type arguments of return type of the method " + methodName + " must be a class!"
                    );
                }
            }
            /*
               We have a tuple of things as value, which
               would like this:

               public interface School extends Model<School> {
                   interface Students extends Var<Tuple<Person>> {}
                   interface Teachers extends Var<Tuple<Person>> {}
               }

               So we need to get the parameter type of the tuple.
               Which in the above example would be: Person.class
            */
            var actualTypeArguments = ((ParameterizedType)type).getActualTypeArguments();
            if ( actualTypeArguments == null || actualTypeArguments.length != 1 ) {
                throw new IllegalArgumentException(
                    "Invalid declaration of method '"+methodName+"'!  " +
                    "Expected a single parameterized type, but found: "+type
                );
            } else {
                var paramType = actualTypeArguments[0];
                if ( paramType instanceof Class<?> ) {
                    return new Params.TupleOf((Class<?>) paramType);
                } else {
                    throw new IllegalArgumentException(
                        "Invalid declaration of method  '"+methodName+"'!   "+
                        "Expected a single parameterized type, but found:  "+paramType
                    );
                }
            }
        } else if ( type instanceof Class ) {
            return new Params.Single((Class<?>) type);
        } else {
            throw new IllegalArgumentException(
                "Invalid declaration of method '"+methodName+"'! " +
                "Property parameter type of method return type not recognizable!"
            );
        }
    }

    public String name() {
        if ( kind == FieldKind.FOREIGN_KEY )
            return EntityTable.FK_PREFIX + baseName() + EntityTable.FK_POSTFIX;
        return baseName();
    }

    public boolean isField(String name) {
        return baseName().equals(name);
    }

    public boolean isList() {
        if ( wrapperType == null )
            return false;
        return Vals.class.isAssignableFrom(wrapperType);
    }

    public boolean isTuple() {
        if ( wrapperType == null )
            return false;
        return Tuple.class.isAssignableFrom(wrapperType);
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
        return name() + " " + AbstractDataBase._fromJavaTypeToDBType(itemType);
    }

    public Optional<EntityTable> getIntermediateTable() {
        if (requiresIntermediateTable())
            return Optional.of(new IntermediateTable(this));
        else
            return Optional.empty();
    }

    public ProxyRef<Val<Object>> asProperty(SQLiteDataBase db, int id, boolean eager ) {
        if ( wrapperType == null )
            throw new IllegalStateException(
                            "Cannot create a property proxy for a field that does not have a wrapper type."
                        );

        var prop = new ModelProperty(
                        db, id, this.name(),
                        AbstractDataBase._tableNameFromClass(ownerModelClass),
                        this.isTuple()
                                ? new EntityTableField.Params.TupleOf(itemType)
                                : new EntityTableField.Params.Single(itemType),
                        allowNull,
                        eager
                    );

        // Let's check if the property is a Val
        boolean isVal = Val.class.isAssignableFrom(wrapperType);
        if (!isVal)
            throw new IllegalArgumentException(
                    "The return type of the method " + baseName() + " is not a subclass " +
                            "of " + Val.class.getName() + " or " + Vals.class.getName() + " with one type parameter"
            );

        // Let's create the proxy:
        return new ProxyRef<>((Val<Object>) Proxy.newProxyInstance(
                        wrapperType.getClassLoader(),
                        new Class[]{wrapperType, Viewable.class},
                        (proxy, method, args) -> {
                            return _handleInvocation(proxy, method, args, prop, wrapperType);
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
        String name = name();
        if (!DataBaseEntity.class.isAssignableFrom(itemType)) {
            String properties = allowNull ? "" : " NOT NULL";
            if (name.equals(EntityTable.ID))
                properties += " PRIMARY KEY AUTOINCREMENT";
            return Optional.of(name + " " + AbstractDataBase._fromJavaTypeToDBType(itemType) + properties);
        } else if ( kind == FieldKind.FOREIGN_KEY) {
            String otherTable = AbstractDataBase._tableNameFromClass(itemType);
            return Optional.of(name + " INTEGER REFERENCES " + otherTable + "("+ EntityTable.ID+")");
        } else if ( kind == FieldKind.INTERMEDIATE_TABLE) {
            return Optional.empty(); // The field is not a column in the table, but a table itself
        } else
            throw new IllegalStateException("Unknown field kind: " + kind);
    }

    public @Nullable Object getDefaultValue() {
        if ( kind == FieldKind.FOREIGN_KEY )
            return null;
        else if ( kind == FieldKind.INTERMEDIATE_TABLE )
            return null;
        else if ( kind == FieldKind.PRIMITIVE) {
            if ( itemType == String.class )
                return "";
            else if ( itemType == Integer.class )
                return 0;
            else if ( itemType == Double.class )
                return 0.0;
            else if ( itemType == Boolean.class )
                return false;
            else if ( itemType == Long.class )
                return 0L;
            else if ( itemType == Float.class )
                return 0.0f;
            else if ( itemType == Short.class )
                return (short) 0;
            else if ( itemType == Byte.class )
                return (byte) 0;
            else if ( Enum.class.isAssignableFrom(itemType) )
                return itemType.getEnumConstants()[0];
            else
                throw new IllegalStateException( "Unknown property type: " + itemType);
        } else if ( kind == FieldKind.ID ) {
            return 1;
        } else
            throw new IllegalStateException("Unknown field kind: " + kind);
    }

    public ProxyRef<Vals<Object>> asProperties( SQLiteDataBase db, int id, boolean eager ) {
        if ( wrapperType == null )
            throw new IllegalStateException(
                    "Cannot create a property list proxy type for a field that does not have a wrapper type."
            );
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
        EntityTable intermediateTable = getIntermediateTable().orElse(null);
        // We expect it to exist:
        if (intermediateTable == null)
            throw new IllegalStateException("The intermediate table does not exist");


        Vars<Object> vars = new ModelProperties(db, ownerModelClass, itemType, intermediateTable, id, eager);

        // Let's create the proxy:
        return new ProxyRef<>((Vals<Object>) Proxy.newProxyInstance(
                        wrapperType.getClassLoader(),
                        new Class[]{wrapperType, Viewables.class},
                        (proxy, method, args) -> {
                            return _handleInvocation(proxy, method, args, vars, wrapperType);
                        }
                    ),
                    vars
                );
    }

    @Override public String toString() {
        return "TableField[" + "baseName=" + name() + ", type=" + itemType + ", kind=" + kind + ']';
    }

}
