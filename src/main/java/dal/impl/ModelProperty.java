package dal.impl;

import dal.api.DataBaseEntity;
import dal.api.Model;
import dal.api.Value;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sprouts.*;
import sprouts.Observable;
import sprouts.Observer;
import sprouts.impl.PropertyChangeListeners;
import sprouts.impl.Sprouts;

import java.util.*;

@NullMarked
final class ModelProperty implements Var<Object>, Viewable<Object>
{
    private static final Logger log = LoggerFactory.getLogger(ModelProperty.class);

    private final SQLiteDataBase _dataBase;
    private final int _id;
    private final String _fieldName;
    private final String _tableName;
    private final EntityTableField.Params _itemType;
    private final boolean _allowNull;
    private final boolean _isEager;

    private @Nullable Object _value;
    private boolean _wasSet = false;

    // Observers:
    private final PropertyChangeListeners<Object> _listeners = new PropertyChangeListeners<>();

    ModelProperty(
        SQLiteDataBase dataBase,
        int id,
        String fieldName,
        String tableName,
        EntityTableField.Params propertyValueType,
        boolean allowNull,
        boolean isEager
    ) {
        _dataBase          = dataBase;
        _id                = id;
        _fieldName         = fieldName;
        _tableName         = tableName;
        _itemType          = propertyValueType;
        _allowNull         = allowNull;
        _isEager           = isEager;
    }

    @Override
    public @Nullable Object orElseNull()
    {
        if ( _wasSet && !_isEager ) return _value;

        Object itemToReturn;
        String select = "SELECT " + _fieldName +
                        " FROM " + _tableName +
                        " WHERE id = ?";

        Map<String, List<Object>> result = _dataBase._query(select, Collections.singletonList(_id));
        if (result.isEmpty())
            return null;
        else {
            List<Object> queryResultColumn = result.get(_fieldName);
            Objects.requireNonNull(queryResultColumn, "Query result column was empty");
            if (queryResultColumn.isEmpty())
                throw new IllegalStateException("Failed to find table entry for id " + _id);
            else if (queryResultColumn.size() > 1)
                throw new IllegalStateException("Found more than one table entry for id " + _id);
            else
                itemToReturn = queryResultColumn.get(0);
        }

        if (!Model.class.isAssignableFrom(_itemType.type())) {
            if ( Enum.class.isAssignableFrom(_itemType.type()) ) {
                // We parse the enum value
                if (itemToReturn == null)
                    return null;
                else {
                    try {
                        return Enum.valueOf((Class<Enum>) _itemType.type(), itemToReturn.toString());
                    } catch ( IllegalArgumentException e ) {
                        throw new IllegalStateException(
                                "Failed to parse enum value " + itemToReturn + " for type " + _itemType.type().getName()
                            );
                    }
                }
            }
            return itemToReturn;
        } else {
            // A foreign key to another model! We already have the id, so we can just create the model
            // and return it.
            // But first let's check if the object we found is not null and actually a number
            if (itemToReturn == null)
                throw new IllegalStateException("The foreign key value is null");
            else if (!Number.class.isAssignableFrom(itemToReturn.getClass()))
                throw new IllegalStateException("The foreign key value is not a number");
            else {
                if (Objects.equals(itemToReturn, 0) )
                    return null;
                // We have a number, so we can find the model
                int foreignKeyId = ((Number) itemToReturn).intValue();
                Class<? extends Model<?>> foreignKeyModelClass = (Class<? extends Model<?>>) _itemType.type();
                itemToReturn = _dataBase.select((Class) foreignKeyModelClass, foreignKeyId);
                if (itemToReturn == null)
                    throw new IllegalStateException("Failed to find model of type " + foreignKeyModelClass.getName() + " with id " + foreignKeyId);
                else
                    return itemToReturn;
            }
        }
    }

    @Override
    public Var<Object> set( Channel channel, Object newItem ) {
        Objects.requireNonNull(channel);
        if ( newItem == null && !_allowNull )
            throw new NullPointerException("Cannot set a null value to a non-nullable property");
        _setNonSilent(newItem, channel);
        return this;
    }

    private void _setNonSilent( Object newItem, Channel channel ) {
        Object oldValue;
        if ( _isEager ) {
            oldValue = orElseNull();
            _set(newItem);
        } else {
            if ( _wasSet ) oldValue = _value;
            else oldValue = orElseNull();
            _value = newItem;
        }
        _wasSet = true;
        if ( !Val.equals( oldValue, newItem ) )
            _listeners.fireChange(this, channel, newItem, oldValue);
    }

    private void _set( Object newItem ) {
        if (!(newItem instanceof DataBaseEntity)) {
            String update = "UPDATE " + _tableName + " " +
                            "SET " + _fieldName + " = ? " +
                            "WHERE id = ?";

            Object valueToStore = newItem;
            if ( Enum.class.isAssignableFrom(_itemType.type()) ) {
                if ( newItem == null )
                    valueToStore = null;
                else
                    valueToStore = newItem.toString();
            }
            if ( _itemType instanceof EntityTableField.Params.TupleOf ) {
                // TODO
            }
            boolean success = _dataBase._update(update, Arrays.asList(valueToStore, _id));
            if (!success)
                throw new IllegalStateException("Failed to update table entry for id " + _id);
        } else if (newItem instanceof Model) {
            // We have a model, so we need to update the foreign key
            Model<?> model = (Model<?>) newItem;
            boolean success = _updateField(model.id().get());
            if ( !success )
                throw new IllegalStateException("Failed to update table entry for id " + _id);
        } else if (newItem instanceof Value) {
            Value dataBaseValue = (Value) newItem;
            int id = _dataBase._storeValueAndIncreaseCounter(dataBaseValue);
            boolean success = _updateField(id);
            if ( !success )
                throw new IllegalStateException("Failed to update table entry for id " + _id);
        } else {
            throw new IllegalStateException("Unknown type for property field '" + _fieldName + "' " +
                                            "of type " + _itemType.type().getName() + ". " +
                                            "Expected a model or a value, but got " + newItem.getClass().getName());
        }
    }

    private boolean _updateField( Object newItem ) {
        StringBuilder update = new StringBuilder();
        update.append("UPDATE ");
        update.append(_tableName);
        update.append(" SET ");
        update.append(_fieldName);
        update.append(" = ? WHERE id = ?");
        return _dataBase._update(update.toString(), Arrays.asList(newItem, _id));
    }

    @Override public Var<Object> withId(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Viewable<Object> onChange( Channel channel, Action<ValDelegate<Object>> action ) {
        _listeners.onChange(channel, action);
        return this;
    }

    @Override
    public Var<Object> fireChange(Channel channel) {
        _listeners.fireChange(this, channel, _value, _value);
        return this;
    }

    @Override public String id() {
        return Sprouts.factory().defaultId();
    }

    @Override public Class<Object> type() {
        if ( _itemType instanceof EntityTableField.Params.Single ) {
            return (Class<Object>) _itemType.type();
        } else if ( _itemType instanceof EntityTableField.Params.TupleOf ) {
            return (Class) Tuple.class;
        }
        throw new UnsupportedOperationException();
    }

    @Override public boolean allowsNull() {
        return _allowNull;
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    boolean wasSet() {
        return _wasSet;
    }

    @Nullable Object getSetVal() {
        return _value;
    }

    String getFieldName() {
        return _fieldName;
    }

    @Override
    public Observable subscribe(Observer listener) {
        _listeners.onChange(listener);
        return this;
    }

    @Override
    public Observable unsubscribe(Subscriber listener) {
        _listeners.unsubscribe(listener);
        return this;
    }

    @Override
    public void unsubscribeAll() {
        _listeners.unsubscribeAll();
    }

    public long numberOfChangeListeners() {
        return _listeners.numberOfChangeListeners();
    }
}
