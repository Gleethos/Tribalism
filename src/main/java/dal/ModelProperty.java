package dal;

import dal.api.Model;
import swingtree.api.mvvm.Action;
import swingtree.api.mvvm.Val;
import swingtree.api.mvvm.ValDelegate;
import swingtree.api.mvvm.Var;

import java.util.*;
import java.util.function.Function;

class ModelProperty implements Var<Object>
{
    private final SQLiteDataBase _dataBase;
    private final int _id;
    private final String _fieldName;
    private final String _tableName;
    private final Class<?> _propertyValueType;

    ModelProperty(SQLiteDataBase dataBase, int id, String fieldName, String tableName, Class<?> propertyValueType) {
        _dataBase = dataBase;
        _id = id;
        _fieldName = fieldName;
        _tableName = tableName;
        _propertyValueType = propertyValueType;
    }

    @Override
    public Object orElseThrow() {
        Object o = _get();
        if (o == null)
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
            return null;
        else {
            List<Object> values = result.get(_fieldName);
            if (values.isEmpty())
                throw new IllegalStateException("Failed to find table entry for id " + _id);
            else if (values.size() > 1)
                throw new IllegalStateException("Found more than one table entry for id " + _id);
            else
                value = values.get(0);
        }

        if (!Model.class.isAssignableFrom(_propertyValueType))
            return value;
        else {
            // A foreign key to another model! We already have the id, so we can just create the model
            // and return it.
            // But first let's check if the object we found is not null and actually a number
            if (value == null)
                throw new IllegalStateException("The foreign key value is null");
            else if (!Number.class.isAssignableFrom(value.getClass()))
                throw new IllegalStateException("The foreign key value is not a number");
            else {
                // We have a number, so we can find the model
                int foreignKeyId = ((Number) value).intValue();
                Class<? extends Model<?>> foreignKeyModelClass = (Class<? extends Model<?>>) _propertyValueType;
                value = _dataBase.select((Class) foreignKeyModelClass, foreignKeyId);
                if (value == null)
                    throw new IllegalStateException("Failed to find model of type " + foreignKeyModelClass.getName() + " with id " + foreignKeyId);
                else
                    return value;
            }
        }
    }

    @Override
    public Var<Object> set(Object newItem) {
        if (!(newItem instanceof Model<?>)) {
            String update = "UPDATE " + _tableName +
                    " SET " + _fieldName +
                    " = ? WHERE id = ?";
            boolean success = _dataBase._update(update, Arrays.asList(newItem, _id));
            if (!success)
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
            if (!success)
                throw new IllegalStateException("Failed to update table entry for id " + _id);
        }
        return this;
    }

    @Override
    public Var<Object> withId(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Var<Object> onAct(Action<ValDelegate<Object>> action) {
        return this;
    }

    @Override
    public Var<Object> act() {
        return this;
    }

    @Override
    public Var<Object> act(Object newValue) {
        return set(newValue);
    }

    @Override
    public Object orElseNullable(Object other) {
        var o = _get();
        if (o == null)
            return other;
        else
            return o;
    }

    @Override
    public boolean isPresent() {
        return orElseNull() != null;
    }

    @Override
    public <U> Val<U> viewAs(Class<U> type, Function<Object, U> mapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String id() {
        return "";
    }

    @Override
    public Class<Object> type() {
        return (Class<Object>) _propertyValueType;
    }

    @Override
    public Val<Object> onShow(Action<ValDelegate<Object>> displayAction) {
        return this;
    }

    @Override
    public Val<Object> show() {
        return this;
    }

    @Override
    public boolean allowsNull() {
        return true;
    }
}
