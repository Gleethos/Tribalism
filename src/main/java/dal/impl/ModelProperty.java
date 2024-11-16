package dal.impl;

import dal.api.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sprouts.Observable;
import sprouts.Observer;
import sprouts.*;
import sprouts.impl.Sprouts;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

class ModelProperty implements Var<Object>, Viewable<Object>
{
    private static final Logger log = LoggerFactory.getLogger(ModelProperty.class);
    private final SQLiteDataBase _dataBase;
    private final int _id;
    private final String _fieldName;
    private final String _tableName;
    private final Class<?> _propertyValueType;
    private final boolean _allowNull;
    private final boolean _isEager;
    private Object _value;
    private boolean _wasSet = false;

    // Observers:

    private final List<Action<ValDelegate<Object>>> _showActions = new ArrayList<>();
    private final List<Action<ValDelegate<Object>>> _actActions = new ArrayList<>();
    private final List<Consumer<Object>> _viewers = new ArrayList<>(0);


    ModelProperty(
            SQLiteDataBase dataBase,
            int id,
            String fieldName,
            String tableName,
            Class<?> propertyValueType,
            boolean allowNull,
            boolean isEager
    ) {
        _dataBase = dataBase;
        _id = id;
        _fieldName = fieldName;
        _tableName = tableName;
        _propertyValueType = propertyValueType;
        _allowNull = allowNull;
        _isEager = isEager;
    }

    @Override
    public Object orElseNull()
    {
        if ( _wasSet && !_isEager ) return _value;

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
    public Var<Object> set( Channel channel, Object newItem ) {
        if ( channel == From.VIEW_MODEL )
            _setNonSilent(newItem);
        else if ( channel == From.VIEW )
            act(newItem);
        return this;
    }

    private void _setNonSilent( Object newItem ) {
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
            fireSet();
    }

    private void _set( Object newItem ) {
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
            if ( !success )
                throw new IllegalStateException("Failed to update table entry for id " + _id);
        }
    }

    @Override public Var<Object> withId(String id) { throw new UnsupportedOperationException(); }

    @Override
    public Viewable<Object> onChange( Channel channel, Action<ValDelegate<Object>> action ) {
        if ( channel == From.VIEW )
            _actActions.add(action);
        if ( channel == From.VIEW_MODEL )
            _showActions.add(action);
        return this;
    }

    @Override
    public Var<Object> fireChange(Channel channel) {
        if ( channel == From.VIEW )
            fireAct();
        if ( channel == From.VIEW_MODEL )
            fireSet();
        return this;
    }

    private Var<Object> fireAct() {
        _triggerActions(From.ALL, _actActions);
        _viewers.forEach( v -> v.accept(_value) );
        return this;
    }

    private Var<Object> act(Object newItem) {
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
            fireAct();

        return this;
    }

    @Override
    public <U> Viewable<U> viewAs(Class<U> type, Function<Object, U> mapper) {
        Var<U> var = mapTo(type, mapper);
        // Now we register a live update listener to this property
        this.onChange(From.VIEW_MODEL,  v -> var.set( mapper.apply( v.orElseNull() ) ));
        _viewers.add( v -> var.set(From.VIEW,  mapper.apply( v ) ) );
        return Viewable.cast(var);
    }

    @Override public String id() { return Sprouts.factory().defaultId(); }

    @Override public Class<Object> type() { return (Class<Object>) _propertyValueType; }

    private void fireSet() {
        _triggerActions(From.ALL, _showActions);
    }

    @Override public boolean allowsNull() { return _allowNull; }

    @Override
    public boolean isMutable() {
        return true;
    }


    protected void _triggerActions(
           Channel source, List<Action<ValDelegate<Object>>> actions
    ) {
        List<Action<ValDelegate<Object>>> removableActions = new ArrayList<>();
        for ( Action<ValDelegate<Object>> action : new ArrayList<>(actions) ) // We copy the list to avoid concurrent modification
            try {
                action.accept(new ModelPropertyDelegate<>(source, this));
            } catch ( Exception e ) {
                log.error("Failed to trigger action", e);
            }
        actions.removeAll(removableActions);
    }

    public boolean wasSet() { return _wasSet; }

    public Object getSetVal() { return _value; }

    public String getFieldName() { return _fieldName; }

    @Override
    public Observable subscribe(Observer listener) {
        throw new IllegalStateException(); // TODO
    }

    @Override
    public Observable unsubscribe(Subscriber listener) {
        throw new IllegalStateException(); // TODO
    }
}
