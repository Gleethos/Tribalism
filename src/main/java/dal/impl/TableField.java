package dal.impl;

import dal.api.Model;
import sprouts.Val;
import sprouts.Vals;
import sprouts.Var;
import sprouts.Vars;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

final class TableField {

    private final Method _method; // The method from the model class
    private final Class<? extends Model<?>> _ownerModelClass; // The model class
    private final Class<?> _propertyType; // The type of the property and return type of the method
    private final Class<?> _propertyValueType; // The type of the property value
    private final FieldKind _kind;
    private final boolean _allowNull = false;


    TableField(
        Method method,
        Class<? extends Model<?>> ownerClass,
        List<Class<? extends Model<?>>> otherModels
    ) {
        _method          = method;
        _ownerModelClass = ownerClass;
        _propertyType    = method.getReturnType();

        // First we check if the return type is a subclass of Val or Vals
        boolean isSubTypeOfVal  = Val.class.isAssignableFrom(_propertyType);
        boolean isSubTypeOfVals = Vals.class.isAssignableFrom(_propertyType);
        boolean isValOrVar   = _propertyType == Val.class  || _propertyType == Var.class;
        boolean isValsOrVars = _propertyType == Vals.class || _propertyType == Vars.class;

        if ( !isSubTypeOfVal && !isSubTypeOfVals )
            throw new IllegalArgumentException(
                    "The return type of the method " + method.getName() + " is not a subclass of " +
                    "either " + Val.class.getName() + " or " + Vals.class.getName() + "."
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
            TypeVariable<?>[] typeParameters = _propertyType.getTypeParameters();
            if (typeParameters.length != 0)
                throw new IllegalArgumentException(
                        "The return type of the method " + method.getName() + " may not have generic parameters!"
                );
            Type[] genericInterfaces = _propertyType.getGenericInterfaces();
            if (genericInterfaces.length != 1)
                throw new IllegalArgumentException(
                        "The return type of the method " + method.getName() + " must implement exactly one interface!"
                );

            Type genericInterface = genericInterfaces[0];
            Type[] actualTypeArguments = ((ParameterizedType) genericInterface).getActualTypeArguments();
            _propertyValueType = (Class<?>) actualTypeArguments[0];
        } else {
            // The return type is Val<T>, Var<T>, Vals<T> or Vars<T> so we can get the type parameter T easily:
            // However we can not get the declared type from Var,Val... it is a generic type...
            // Instead, we get the type from the method parameter
            var declaredReturnTypeGenericParam = method.getGenericReturnType();
            if ( declaredReturnTypeGenericParam instanceof ParameterizedType ) {
                var declaredReturnTypeGenericParamType = ((ParameterizedType) declaredReturnTypeGenericParam).getActualTypeArguments()[0];
                if ( declaredReturnTypeGenericParamType instanceof Class<?> ) {
                    _propertyValueType = (Class<?>) declaredReturnTypeGenericParamType;
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
        if (method.getName().equals("id")) {
            if (!_propertyType.equals(Model.Id.class))
                throw new IllegalArgumentException(
                        "The return type of the method " + method.getName() + " is not " + Model.Id.class.getName()
                );
            _kind = FieldKind.ID;
        }
        // Then we check if the field is a foreign key field
        else if ( isSubTypeOfVal ) {
            if (Model.class.isAssignableFrom(_propertyValueType)) {
                if (otherModels.contains(_propertyValueType)) {
                    _kind = FieldKind.FOREIGN_KEY;
                } else
                    throw new IllegalArgumentException(
                        "Cannot establish table field for method '" + method.getName() + "()' for model '" + _ownerModelClass.getName() + "', \n" +
                        "because the return type of " +
                        "the method is a property referencing another model called '" + _propertyValueType.getName() + "', " +
                        "which is however not known " +
                        "by the database, please make sure that it is passed to the 'createTablesFor(..)' method alongside " +
                        "all other model types!"
                    );
            } else if (AbstractDataBase._isBasicDataType(_propertyValueType)) {
                _kind = FieldKind.VALUE;
            } else {
                boolean propertyValueIsModel = Model.class.isAssignableFrom(_propertyValueType);
                if ( !propertyValueIsModel )
                    throw new IllegalArgumentException(
                            "Failed to create table field '" + _method.getName() + "' for model '" + _ownerModelClass.getName() + "', because \n" +
                            "the property value type '" + _propertyValueType.getName() + "' in declared method " +
                            "'public " + _propertyType.getSimpleName() + "<" + _propertyValueType.getSimpleName() + "> " + method.getName() + "();' " +
                            "is not a basic data type and is also not recognisable as another model! \n" +
                            "If you want this declaration to work, make sure that '" + _propertyValueType.getName() + "' is a subtype of the '" + Model.class.getName() + "' interface " +
                            "and also is passed to the the 'createTablesFor(Class<M>... models);' method."
                        );
                else // The user has simply not passed the interface class to the createTablesFor(Class<Model... models) method:
                    throw new IllegalArgumentException(
                            "Failed to create table field '" + _method.getName() + "' for model '" + _ownerModelClass.getName() + "', because \n" +
                            "the property value type '" + _propertyValueType.getName() + "' in declared method " +
                            "'public " + _propertyType.getSimpleName() + "<" + _propertyValueType.getSimpleName() + "> " + method.getName() + "();' " +
                            "is a model type not known to the database! " +
                            "If you want this declaration to work, make sure that you have passed the interface class of the model to the " +
                            "createTablesFor(Class<M>... models); method!"
                        );
            }
        }
        // Then we check if the field is an intermediate table field
        else if (isSubTypeOfVals) {
            if (otherModels.contains(_propertyValueType)) {
                _kind = FieldKind.INTERMEDIATE_TABLE;
            } else {
                if (AbstractDataBase._isBasicDataType(_propertyValueType))
                    throw new IllegalArgumentException(
                            "List of basic data types cannot be modelled as table fields."
                    );
                else
                    throw new IllegalArgumentException(
                            "The type '" + _propertyType.getName() + "' of the property returned by " +
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
        if ( _kind == FieldKind.FOREIGN_KEY )
            return ModelTable.FK_PREFIX + _method.getName() + ModelTable.FK_POSTFIX;
        return _method.getName();
    }

    public String getMethodName() {
        return _method.getName();
    }

    public boolean isField(String name) {
        return _method.getName().equals(name);
    }

    public Class<?> getType() {
        return _propertyValueType;
    }

    public Class<?> getPropType() {
        return _propertyType;
    }

    public boolean isList() {
        return Vals.class.isAssignableFrom(_propertyType);
    }

    public FieldKind getKind() {
        return _kind;
    }

    public boolean requiresIntermediateTable() {
        return _kind == FieldKind.INTERMEDIATE_TABLE;
    }

    public boolean isForeignKey() {
        return _kind == FieldKind.FOREIGN_KEY;
    }

    public String toTableFieldStatement() {
        return getName() + " " + AbstractDataBase._fromJavaTypeToDBType(_propertyValueType);
    }

    public Optional<ModelTable> getIntermediateTable() {
        if (requiresIntermediateTable())
            return Optional.of(new ModelTable() {
                @Override
                public String getTableName() {
                    return TableField.this.getName() + INTER_TABLE_POSTFIX;
                }

                @Override
                public List<TableField> getFields() {
                    return Collections.emptyList();
                }

                @Override
                public List<Class<? extends Model<?>>> getReferencedModels() {
                    Class<?> thisTableClass = TableField.this._method.getDeclaringClass();
                    Class<?> otherTableClass = TableField.this._propertyValueType;
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
                    Class<?> thisTableClass = TableField.this._method.getDeclaringClass();
                    Class<?> otherTableClass = TableField.this._propertyValueType;
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
                    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                }

            });
        else
            return Optional.empty();
    }

    public ProxyRef<Val<Object>> asProperty(SQLiteDataBase db, int id, boolean eager ) {
        var prop = new ModelProperty(
                        db, id, this.getName(),
                        AbstractDataBase._tableNameFromClass(_ownerModelClass),
                        _propertyValueType,
                        _allowNull,
                        eager
                    );

        Class<?> propertyType = _propertyType;

        // Let's check if the property is a Val
        boolean isVal = Val.class.isAssignableFrom(propertyType);
        if (!isVal)
            throw new IllegalArgumentException(
                    "The return type of the method " + _method.getName() + " is not a subclass " +
                            "of " + Val.class.getName() + " or " + Vals.class.getName() + " with one type parameter"
            );

        // Let's create the proxy:
        return new ProxyRef<>((Val<Object>) Proxy.newProxyInstance(
                        propertyType.getClassLoader(),
                        new Class[]{propertyType},
                        (proxy, method, args) -> {
                            String methodName = method.getName();
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
                            }
                            // We simply delegate to the property
                            return method.invoke(prop, args);
                        }
                    ),
                    prop
                );
    }

    public Optional<String> asSqlColumn() {
        String name = getName();
        if (!Model.class.isAssignableFrom(_propertyValueType)) {
            String properties = _allowNull ? "" : " NOT NULL";
            if (name.equals("id"))
                properties += " PRIMARY KEY AUTOINCREMENT";
            return Optional.of(name + " " + AbstractDataBase._fromJavaTypeToDBType(_propertyValueType) + properties);
        } else if ( _kind == FieldKind.FOREIGN_KEY) {
            String otherTable = AbstractDataBase._tableNameFromClass(_propertyValueType);
            return Optional.of(name + " INTEGER REFERENCES " + otherTable + "(id)");
        } else if ( _kind == FieldKind.INTERMEDIATE_TABLE) {
            return Optional.empty(); // The field is not a column in the table, but a table itself
        } else
            throw new IllegalStateException("Unknown field kind: " + _kind);
    }

    public Object getDefaultValue() {
        if ( _kind == FieldKind.FOREIGN_KEY )
            return null;
        else if ( _kind == FieldKind.INTERMEDIATE_TABLE )
            return null;
        else if ( _kind == FieldKind.VALUE ) {
            if ( _propertyValueType == String.class )
                return "";
            else if ( _propertyValueType == Integer.class )
                return 0;
            else if ( _propertyValueType == Double.class )
                return 0.0;
            else if ( _propertyValueType == Boolean.class )
                return false;
            else
                throw new IllegalStateException( "Unknown property type: " + _propertyValueType );
        } else if ( _kind == FieldKind.ID ) {
            return 1;
        } else
            throw new IllegalStateException("Unknown field kind: " + _kind);
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


        Vars<Object> vars = new ModelProperties(db, _ownerModelClass, _propertyValueType, intermediateTable, id, eager);

        // Let's create the proxy:
        return new ProxyRef<>((Vals<Object>) Proxy.newProxyInstance(
                        _propertyType.getClassLoader(),
                        new Class[]{_propertyType},
                        (proxy, method, args) -> {
                            // We simply delegate to the property
                            return method.invoke(vars, args);
                        }
                    ),
                    vars
                );
    }

}
