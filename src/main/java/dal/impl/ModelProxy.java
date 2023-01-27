package dal.impl;

import dal.api.Model;
import swingtree.api.mvvm.Val;
import swingtree.api.mvvm.Vals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

class ModelProxy<T extends Model<T>> implements InvocationHandler {
    private final SQLiteDataBase _dataBase;
    private final ModelTable _modelTable;
    private final int _id;

    public ModelProxy(SQLiteDataBase db, ModelTable table, int id) {
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
            for (var field : _modelTable.getFields()) {
                if (!field.isList()) {
                    Object o = field.asProperty(_dataBase, _id).orElseNull();
                    String asString;
                    if (o == null)
                        asString = "null";
                    else if (o instanceof String)
                        asString = "\"" + o + "\"";
                    else
                        asString = o.toString();

                    sb.append(field.getName());
                    sb.append("=").append(asString);
                    sb.append(", ");
                } else {
                    sb.append(field.getName());
                    sb.append("=[");
                    for (Object o : field.asProperties(_dataBase, _id)) {
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
                    sb.append("], ");
                }
            }
            // remove the last comma
            if (_modelTable.getFields().size() > 0)
                sb.delete(sb.length() - 2, sb.length());
            sb.append("]");
            return sb.toString();
        }

        TableField tableField = _modelTable.getField(methodName);
        if (tableField == null)
            throw new IllegalArgumentException("The model '" + _modelTable.getModelInterface().get().getName() + "' does not have a property named '" + methodName + "'!");
        if (args != null && args.length != 0)
            throw new IllegalArgumentException("The model '" + _modelTable.getModelInterface().get().getName() + "' does not have a setter for the property named '" + methodName + "'!");
        if (method.getReturnType() == void.class)
            throw new IllegalArgumentException("The model '" + _modelTable.getModelInterface().get().getName() + "' does not have a setter for the property named '" + methodName + "'!");

        // Now let's get the property value from the database
        Object toBeReturned;

        if (Val.class.isAssignableFrom(method.getReturnType()))
            toBeReturned = tableField.asProperty(_dataBase, _id);
        else if (Vals.class.isAssignableFrom(method.getReturnType()))
            toBeReturned = tableField.asProperties(_dataBase, _id);
        else
            throw new IllegalArgumentException("The model '" + _modelTable.getModelInterface().get().getName() + "' does not have a property named '" + methodName + "'!");

        // Now let's check if the property is of the correct type
        if (!method.getReturnType().isAssignableFrom(toBeReturned.getClass()))
            throw new IllegalArgumentException("Failed to create a proxy for the model '" + _modelTable.getModelInterface().get().getName() + "' because the property '" + methodName + "' is of type '" + toBeReturned.getClass().getName() + "' but the getter is of type '" + method.getReturnType().getName() + "'!");

        return toBeReturned;
    }
}
