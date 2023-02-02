package dal.impl;

import dal.api.Model;
import sprouts.Val;
import sprouts.Vals;
import sprouts.Var;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

class ModelProxy<T extends Model<T>> implements InvocationHandler {
    private final SQLiteDataBase _dataBase;
    private final ModelTable _modelTable;
    private final int _id;
    private final boolean _isEager;
    private final Map<String, ProxyRef<Object>> cachedPropertyProxies = new HashMap<>();

    public ModelProxy(
        SQLiteDataBase db,
        ModelTable table,
        int id,
        boolean isEager
    ) {
        _dataBase = db;
        _modelTable = table;
        _id = id;
        _isEager = isEager;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        /*
            First let's do the easy stuff: Equals and hashcode
            They are easy because we can just use the id :)
            Let's check:
         */
        if (methodName.equals("equals")) {
            if (args.length != 1)
                throw new IllegalArgumentException("The equals method must have exactly one argument!");
            if (!Model.class.isAssignableFrom(args[0].getClass()))
                return false;
            return _id == ((Model<?>) args[0]).id().get();
        }
        if (methodName.equals("hashCode")) {
            return _id;
        }
        // Something a little more complicated: toString
        if (methodName.equals("toString")) {
            /*
                Let's not be lazy and actually build a string that contains all the properties!
             */
            StringBuilder sb = new StringBuilder();
            sb.append(_modelTable.getModelInterface().map(Class::getSimpleName).orElse(_modelTable.getTableName()));
            sb.append("[");
            for ( var field : _modelTable.getFields() ) {
                if (!field.isList()) {
                    Object o = field.asProperty(_dataBase, _id, true).impl().orElseNull();
                    String asString;
                    if (o == null)
                        asString = "null";
                    else if (o instanceof String)
                        asString = "\"" + o + "\"";
                    else
                        asString = o.toString();

                    sb.append(field.getMethodName());
                    sb.append("=").append(asString);
                    sb.append(", ");
                } else {
                    sb.append(field.getMethodName());
                    sb.append("=[");
                    Vals<Object> props = field.asProperties(_dataBase, _id, true).impl();
                    for ( Object o : props ) {
                        String asString;
                        if (o == null)
                            asString = "null";
                        else if (o instanceof String)
                            asString = "\"" + o + "\"";
                        else
                            asString = o.toString();

                        sb.append(asString);
                        sb.append(", ");
                    }
                    if ( props.size() > 0 )
                        sb.delete(sb.length() - 2, sb.length());
                    sb.append("], ");
                }
            }
            // remove the last comma
            if (_modelTable.getFields().size() > 0)
                sb.delete(sb.length() - 2, sb.length());
            sb.append("]");
            return sb.toString();
        }
        else if ( methodName.equals("commit") ) {
            /*
                Ah! The commit method, this is an interesting one as it is part of the Model interface.
                It looks like this in the interface:

                void commit( Consumer<M> transaction );

                It is expected to accept a Consumer that will receive a non-eager / transactional model proxy
                which can be used to update the database in a single transaction instead of multiple little ones.
                This is useful as a performance optimization.
                Let's check:
             */
            if ( args.length != 1 )
                throw new IllegalArgumentException("The commit method must have exactly one argument!");
            // the first argument is the consumer
            Object consumer = args[0];
            // Let's check if it is a consumer
            if ( !Consumer.class.isAssignableFrom(consumer.getClass()) )
                throw new IllegalArgumentException("The commit method must have a Consumer as its first argument!");

            Consumer<T> c = (Consumer<T>) consumer;
            // Let's create a proxy that is not eager

            var nonEager = new ModelProxy<T>(_dataBase, _modelTable, _id, false);
            // Let's create a proxy that is of the correct type
            var transactionProxy = (T)
                            java.lang.reflect.Proxy.newProxyInstance(
                                _modelTable.getModelInterface().orElseThrow().getClassLoader(),
                                new Class[]{_modelTable.getModelInterface().orElseThrow()},
                                nonEager
                            );

            c.accept(transactionProxy);

            // Now we need to commit the transaction
            nonEager.executeCommit();
            return null;
        } else if ( methodName.equals("clone") ) {
            Class<Model> modelInterface = (Class) _modelTable.getModelInterface().orElseThrow();
            var clone = _dataBase.create(modelInterface);
            var thisModel = _dataBase.select(modelInterface, _id);

            // This is simple, we use reflection to get all the properties and copy them over
            Method[] methods0 = clone.getClass().getMethods();
            Method[] methods1 = thisModel.getClass().getMethods();
            for ( int i = 0; i < methods0.length; i++ ) {
                for ( int ii = 0; ii < methods1.length; ii++ ) {
                    Method m0 = methods0[i];
                    Method m1 = methods1[ii];
                    System.out.println(m0.getName()+" "+m1.getName());
                    if ( m0.getName().equals(m1.getName()) ) {
                        if (!m0.getName().equals("id") && m0.getParameterCount() == 0) {
                            // The return type of the method must be a subtype of Var:
                            if (!Val.class.isAssignableFrom(m0.getReturnType()))
                                continue;

                            Var<Object> cloneProp = (Var<Object>) m0.invoke(clone);
                            Var<Object> thisProp = (Var<Object>) m1.invoke(thisModel);

                            // Now we need to call the set method on the clone and the get method on this
                            cloneProp.set(thisProp.get());
                        }
                    }
                }
            }
            return clone;
        }

        if ( !_modelTable.hasField(methodName) ) {
            /*
                Ah, a method that is not a property! Let's check if it is a default method!
            */
            // First we expect there to be a model interface
            Class<?> modelInterface = _modelTable.getModelInterface().orElseThrow();
            // Then we expect the method to be declared in the model interface
            Method modelInterfaceMethod = modelInterface.getDeclaredMethod(methodName, method.getParameterTypes());
            // Then we expect the method to be a default method
            if (!modelInterfaceMethod.isDefault())
                throw new IllegalArgumentException("Method " + methodName + " is not a property and not a default method!");

            // Perfect! Let's just call the method on the proxy!
            return MethodHandles.lookup()
                    .findSpecial(
                        modelInterface,
                        methodName,
                        MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
                        modelInterface
                    )
                    .bindTo(proxy)
                    .invokeWithArguments(args);
        }

        TableField tableField = _modelTable.getField(methodName);
        if ( tableField == null )
            throw new IllegalArgumentException("The model '" + _modelTable.getModelInterface().get().getName() + "' does not have a property named '" + methodName + "'!");
        if ( args != null && args.length != 0 )
            throw new IllegalArgumentException("The model '" + _modelTable.getModelInterface().get().getName() + "' does not have a setter for the property named '" + methodName + "'!");
        if ( method.getReturnType() == void.class )
            throw new IllegalArgumentException("The model '" + _modelTable.getModelInterface().get().getName() + "' does not have a setter for the property named '" + methodName + "'!");

        // Now let's get the property value from the database
        ProxyRef<Object> toBeReturned;

        if (Val.class.isAssignableFrom(method.getReturnType()))
            toBeReturned = cachedPropertyProxies.computeIfAbsent(methodName, n -> (ProxyRef) tableField.asProperty(_dataBase, _id, _isEager));
        else if (Vals.class.isAssignableFrom(method.getReturnType()))
            toBeReturned = cachedPropertyProxies.computeIfAbsent(methodName, n -> (ProxyRef) tableField.asProperties(_dataBase, _id, _isEager));
        else
            throw new IllegalArgumentException("The model '" + _modelTable.getModelInterface().get().getName() + "' does not have a property named '" + methodName + "'!");

        // Now let's check if the property is of the correct type
        if (!method.getReturnType().isAssignableFrom(toBeReturned.proxy().getClass()))
            throw new IllegalArgumentException("Failed to create a proxy for the model '" + _modelTable.getModelInterface().get().getName() + "' because the property '" + methodName + "' is of type '" + toBeReturned.getClass().getName() + "' but the getter is of type '" + method.getReturnType().getName() + "'!");

        return toBeReturned.proxy();
    }

    public int getId() {
        return _id;
    }

    public String getTableName() {
        return _modelTable.getTableName();
    }

    public void executeCommit() {
        if ( _isEager )
            throw new IllegalStateException("Cannot transact an eager model!");

        List<ModelProperty> properties = cachedPropertyProxies.values().stream()
                                                                .map(ProxyRef::impl)
                                                                .filter(p -> p instanceof ModelProperty)
                                                                .map(p -> (ModelProperty) p)
                                                                .toList();

        // We need to find the properties that have changed
        List<ModelProperty> changedProperties = properties.stream()
                                                            .filter(ModelProperty::wasSet)
                                                            .toList();

        // We need a list of method names and values
        List<String> fieldNames = changedProperties.stream()
                                                    .map(ModelProperty::getFieldName)
                                                    .toList();
        List<Object> values = changedProperties.stream()
                                                .map(ModelProperty::getSetVal)
                                                .toList();

        // Let's check if the number of fields is the same as the number of values
        if ( fieldNames.size() != values.size() )
            throw new IllegalStateException("The number of fields and values is not the same: " + fieldNames.size() + " != " + values.size() + "!");

        // Now we can build the update query
        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ");
        sql.append(_modelTable.getTableName());
        sql.append(" SET ");
        for (int i = 0; i < fieldNames.size(); i++) {
            sql.append(fieldNames.get(i));
            sql.append(" = ?");
            if ( i < fieldNames.size() - 1 )
                sql.append(", ");
        }
        sql.append(" WHERE id = " + _id);

        // Now we can execute the query
        _dataBase._update(sql.toString(), values);
    }

}
