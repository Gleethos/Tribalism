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
    private final long _id;
    private final String _fieldName;
    private final String _tableName;
    private final FieldType.VarOf _fieldType;
    private final boolean _allowNull;
    private final boolean _isEager;

    private @Nullable Object _value;
    private boolean _wasSet = false;

    // Observers:
    private final PropertyChangeListeners<Object> _listeners = new PropertyChangeListeners<>();

    ModelProperty(
        SQLiteDataBase dataBase,
        long id,
        String fieldName,
        String tableName,
        FieldType.VarOf fieldType,
        boolean allowNull,
        boolean isEager
    ) {
        _dataBase          = dataBase;
        _id                = id;
        _fieldName         = fieldName;
        _tableName         = tableName;
        _fieldType         = fieldType;
        _allowNull         = allowNull;
        _isEager           = isEager;
        if ( fieldType instanceof FieldType.VarOf.Tuple ) {
            _value = Tuple.of(fieldType.item());
        }
    }

    @Override
    public @Nullable Object orElseNull()
    {
        if ( _wasSet && !_isEager ) return _value;
        return switch (_fieldType) {
            case FieldType.VarOf.Primitive ignored1 -> internalOrElseNullForInlineProperties();
            case FieldType.VarOf.Model ignored2 -> internalOrElseNullForInlineProperties();
            case FieldType.VarOf.Value ignored3 -> internalOrElseNullForInlineProperties();
            case FieldType.VarOf.Id ignored4 -> internalOrElseNullForInlineProperties();
            case FieldType.VarOf.Tuple varOfTuple -> _readTupleFromIntermediateTable(varOfTuple);
        };
    }

    private Tuple<?> _readTupleFromIntermediateTable(FieldType.VarOf.Tuple varOfTuple) {
        Class<? extends Value> itemType = varOfTuple.item();
        return _dataBase._readTupleFromIntermediateTable(_tableName, _fieldName, _id, itemType);
    }

    private @Nullable Object internalOrElseNullForInlineProperties()
    {
        Object itemToReturn;
        String select = "SELECT " + _fieldName +
                        " FROM " + _tableName +
                        " WHERE id = ?";

        Map<String, List<Object>> result = _dataBase._db._query(select, Collections.singletonList(_id));
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

        if (!Model.class.isAssignableFrom(_fieldType.item())) {
            if ( Enum.class.isAssignableFrom(_fieldType.item()) ) {
                // We parse the enum value
                if (itemToReturn == null)
                    return null;
                else {
                    try {
                        return Enum.valueOf((Class<Enum>) _fieldType.item(), itemToReturn.toString());
                    } catch ( IllegalArgumentException e ) {
                        throw new IllegalStateException(
                                "Failed to parse enum value " + itemToReturn + " for type " + _fieldType.item().getName()
                            );
                    }
                }
            }
            if ( _fieldType.item() == Integer.class ) {
                return ((Number)itemToReturn).intValue(); // SQLite returns a Long, so we convert it to Integer
            }
            if ( Value.class.isAssignableFrom(_fieldType.item()) ) {
                // A foreign key to a value table! Resolve the id back to the actual value record.
                if ( itemToReturn == null )
                    return null;
                if ( !Number.class.isAssignableFrom(itemToReturn.getClass()) )
                    throw new IllegalStateException("The foreign key value is not a number");
                long foreignKeyId = ((Number) itemToReturn).longValue();
                if ( foreignKeyId == 0L )
                    return null;
                Class<? extends Value> foreignKeyValueClass = (Class<? extends Value>) _fieldType.item();
                return _dataBase._readValue(foreignKeyValueClass, foreignKeyId);
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
                if (Objects.equals(itemToReturn, 0L) )
                    return null;
                // We have a number, so we can find the model
                long foreignKeyId = ((Number) itemToReturn).longValue();
                Class<? extends Model<?>> foreignKeyModelClass = (Class<? extends Model<?>>) _fieldType.item();
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
        if ( _fieldType instanceof FieldType.VarOf.Tuple varOfTuple ) {
            _setTuple(varOfTuple, (Tuple<?>) newItem);
            return;
        }
        if (!(newItem instanceof DataBaseEntity)) {
            String update = "UPDATE " + _tableName + " " +
                            "SET " + _fieldName + " = ? " +
                            "WHERE id = ?";

            Object valueToStore = newItem;
            if ( Enum.class.isAssignableFrom(_fieldType.item()) ) {
                if ( newItem == null )
                    valueToStore = null;
                else
                    valueToStore = newItem.toString();
            }
            boolean success = _dataBase._db._update(update, Arrays.asList(valueToStore, _id));
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
            // Before assigning the new value, decrement the old value reference so that
            // the usage counter on the value table stays correct and orphaned rows
            // are cleaned up. Reading the old value from the database reflects the
            // currently-stored FK; it is null only if the property has not been set yet.
            Object oldValue = orElseNull();
            if (oldValue instanceof Value oldVal) {
                _dataBase._removeReferencedValue(_fieldType.item(), oldVal);
            }
            long id = _dataBase._storeReferencedValue(_fieldType.item(), dataBaseValue);
            boolean success = _updateField(id);
            if ( !success )
                throw new IllegalStateException("Failed to update table entry for id " + _id);
        } else {
            throw new IllegalStateException("Unknown type for property field '" + _fieldName + "' " +
                                            "of type " + _fieldType.item().getName() + ". " +
                                            "Expected a model or a value, but got " + newItem.getClass().getName());
        }
    }

    private void _setTuple(FieldType.VarOf.Tuple varOfTuple, Tuple<?> newTuple) {
        Objects.requireNonNull(newTuple, "Cannot set a null tuple");
        Class<? extends Value> itemType = varOfTuple.item();
        // Read the current tuple to know which value usages to decrement:
        Tuple<?> oldTuple = _dataBase._readTupleFromIntermediateTable(_tableName, _fieldName, _id, itemType);
        for (Object o : oldTuple) {
            if (o != null) _dataBase._removeReferencedValue(itemType, (Value) o);
        }
        // Wipe existing rows for this owner in the intermediate table:
        _dataBase._clearIntermediateTable(_tableName, _fieldName, _id);
        // Insert the new tuple items in order:
        int pos = 0;
        for (Object o : newTuple) {
            if (o != null) {
                Value v = (Value) o;
                long valueId = _dataBase._storeReferencedValue(itemType, v);
                _dataBase._insertIntermediateTableRow(_tableName, _fieldName, _id, itemType, valueId, pos);
            }
            pos++;
        }
    }

    private boolean _updateField( Object newItem ) {
        StringBuilder update = new StringBuilder();
        update.append("UPDATE ");
        update.append(_tableName);
        update.append(" SET ");
        update.append(_fieldName);
        update.append(" = ? WHERE id = ?");
        return _dataBase._db._update(update.toString(), Arrays.asList(newItem, _id));
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
        if ( _fieldType instanceof FieldType.VarOf.Tuple ) {
            return (Class) Tuple.class;
        }
        return (Class<Object>) _fieldType.item();
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
