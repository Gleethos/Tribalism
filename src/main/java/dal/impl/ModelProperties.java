package dal.impl;

import dal.api.Model;
import org.jspecify.annotations.NullMarked;
import sprouts.*;
import sprouts.Observable;
import sprouts.Observer;

import java.util.*;

@NullMarked
final class ModelProperties implements Vars<Object>, Viewables<Object>
{
    private final SQLiteDataBase db;
    private final List<Long> ids;
    private final long id; // The id of the model to which the properties belong
    private final IntermediateTable intermediateTable;
    private final String otherTable;
    private final String otherTableIdColumn;
    private final String thisTableIdColumn;
    private final boolean _isEager;

    ModelProperties(
        SQLiteDataBase db,
        Class<?> ownerModelClass,
        FieldType.VarsOf fieldType,
        IntermediateTable intermediateTable,
        long id,
        boolean isEager
    ) {
        this.db = db;
        this.intermediateTable = intermediateTable;
        this.id = id;
        if ( !(intermediateTable.entityField().type() instanceof FieldType.VarsOf) )
            throw new IllegalStateException("Connected to a field which is not a property list!");
        _isEager = isEager;

        // We need to find the name of the column that contains the ids of the models
        // that are referenced by the intermediate table:
        this.otherTable = BasicSQLiteDataBase._tableNameFromClass(fieldType.item());
        this.otherTableIdColumn = EntityTable.INTER_RIGHT_FK_PREFIX + otherTable + EntityTable.INTER_FK_POSTFIX;
        this.thisTableIdColumn = EntityTable.INTER_LEFT_FK_PREFIX + BasicSQLiteDataBase._tableNameFromClass(ownerModelClass) + EntityTable.INTER_FK_POSTFIX;
        String query = "SELECT " + otherTableIdColumn + " FROM " + intermediateTable.getTableName() +
                " WHERE " + thisTableIdColumn + " = ?" +
                " ORDER BY " + EntityTable.INTER_POSITION_COLUMN + " ASC";

        List<Object> param = Collections.singletonList(id);
        Map<String, List<Object>> result = db._db._query(query, param);

        if ( result.size() == 0 )
            result.put(otherTableIdColumn, new ArrayList<>());

        // The column should be named after the id column of the other table:
        if ( !result.containsKey(otherTableIdColumn) )
            throw new IllegalStateException("The column should be named after the id column of the other table");
        // The column should contain a list of ids:
        List<Object> found = result.get(otherTableIdColumn);
        this.ids = new ArrayList<>(found.stream().map(o -> ((Number) o).longValue()).toList());
    }

    private FieldType.VarsOf fieldType() {
        return (FieldType.VarsOf) intermediateTable.entityField().type();
    }

    private Model<?> _select( long id ) {
        // We need to get the model from the database:
        Class<Model> propertyValueType = (Class<Model>) this.fieldType().item();
        Model<?> model = db.select(propertyValueType, id);
        return model;
    }

    @Override
    public Iterator<Object> iterator() {
        // We need to map the ids to the actual models:
        return ids.stream().map(id -> {
            // We need to get the model from the database:
            return (Object) _select(id);
        }).iterator();
    }

    @Override public Class<Object> type() { return (Class<Object>) fieldType().item(); }

    @Override public int size() { return ids.size(); }

    @Override
    public Var<Object> at(int index) {
        FieldType.VarOf varType = fieldType().varOf();
        return new ModelProperty(
                db,
                ids.get(index),
                EntityTable.INTER_RIGHT_FK_PREFIX + otherTable + EntityTable.INTER_FK_POSTFIX,
                intermediateTable.getTableName(),
                varType,
                false,
                _isEager
            );
    }

    @Override
    public Viewables<Object> onChange(Action<ValsDelegate<Object>> action) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Vals<Object> fireChange() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean allowsNull() {
        return false;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public boolean isView() {
        return false;
    }

    @Override
    public Vars<Object> removeAt(int index)
    {
        if ( !_isEager )
            throw new UnsupportedOperationException("Transactional modification of lists (intermediate tables) is not supported yet.");
        _removeAt(index);
        return this;
    }

    @Override
    public Vars<Object> popRange(int from, int to) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Vars<Object> removeRange(int from, int to) {
        if ( !_isEager )
            throw new UnsupportedOperationException("Transactional modification of lists (intermediate tables) is not supported yet.");
        for ( int i = to - 1; i >= from; i-- ) {
            _removeAt(i);
        }
        return this;
    }

    private void _removeAt(int index) {
        /*
            Basically all we need to do is delete a row from the intermediate table!
            Which row? The one that contains the id of the left model and the id of the
            model at the given index.
        */
        long leftId = id;
        long rightId = ids.get(index);
        String query = "DELETE FROM " + intermediateTable.getTableName() + " " +
                "WHERE " + thisTableIdColumn + " = ? AND " + EntityTable.INTER_POSITION_COLUMN + " = ?";
        List<Object> params = List.of(leftId, index);
        db._db._update(query, params);
        // Shift positions of subsequent entries down to keep dense numbering:
        String shift = "UPDATE " + intermediateTable.getTableName() +
                " SET " + EntityTable.INTER_POSITION_COLUMN + " = " + EntityTable.INTER_POSITION_COLUMN + " - 1" +
                " WHERE " + thisTableIdColumn + " = ?" +
                " AND " + EntityTable.INTER_POSITION_COLUMN + " > ?";
        db._db._update(shift, List.of(leftId, index));
        ids.remove(index);
    }

    @Override
    public Vars<Object> addAt( int index, Var<Object> var )
    {
        if ( !_isEager )
            throw new UnsupportedOperationException("Transactional modification of lists (intermediate tables) is not supported yet.");

        Objects.requireNonNull(var);
        // First let's verify the type:
        if ( !fieldType().item().isAssignableFrom(var.type()) )
            throw new IllegalArgumentException("The type of the var is not the same as the type of the property");

        /*
            We need to insert a row into the intermediate table! Basic stuff...
        */
        Object o = var.get();
        long leftId = id;
        long rightId = ((Model) o).id().get();
        // Shift positions of any existing entries at or after the insertion index:
        String shift = "UPDATE " + intermediateTable.getTableName() +
                " SET " + EntityTable.INTER_POSITION_COLUMN + " = " + EntityTable.INTER_POSITION_COLUMN + " + 1" +
                " WHERE " + thisTableIdColumn + " = ?" +
                " AND " + EntityTable.INTER_POSITION_COLUMN + " >= ?";
        db._db._update(shift, List.of(leftId, index));
        String query = "INSERT INTO " + intermediateTable.getTableName() + " " +
                "(" + thisTableIdColumn + ", " + otherTableIdColumn + ", " + EntityTable.INTER_POSITION_COLUMN + ") " +
                "VALUES (?, ?, ?)";
        List<Object> params = List.of(leftId, rightId, index);
        db._db._update(query, params);
        ids.add(index, rightId);
        return this;
    }

    @Override
    public Vars<Object> setAt( int index, Var<Object> var )
    {
        if ( !_isEager )
            throw new UnsupportedOperationException("Transactional modification of lists (intermediate tables) is not supported yet.");

        Objects.requireNonNull(var);
        /*
            This is a bit more complicated.
            We need to update the row in the intermediate table.
            More specifically, we need to update the id of the model that is referenced
            by the intermediate table.
        */
        long leftId = id;
        long rightId = (Integer) var.get();
        long oldRightId = ids.get(index);

        String update = "UPDATE " + intermediateTable.getTableName() +
                " SET " + otherTableIdColumn +
                " = ? WHERE id = ?";

        List<Object> params = List.of(rightId, oldRightId);
        db._db._update(update, params);
        ids.set(index, rightId);
        return this;
    }

    @Override
    public Vars<Object> setRange(int from, int to, Object value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Vars<Object> setRange(int from, int to, Var<Object> value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Vars<Object> addAllAt(int index, Vars<Object> vars) {
        for ( int i = 0; i < vars.size(); i++ ) {
            addAt(index + i, vars.at(i));
        }
        return this;
    }

    @Override
    public Vars<Object> setAllAt(int index, Vars<Object> vars) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Vars<Object> retainAll(Vals<Object> vars) {
        Vars<Object> toRemove = Vars.of(this.type());
        for ( Object o : this ) {
            if ( !vars.contains(o) )
                toRemove.add(o);
        }
        this.removeAll(toRemove);
        return this;
    }

    @Override
    public Vars<Object> removeLast(int count) {
        Vars<Object> toRemove = Vars.of(this.type());
        for ( int i = 0; i < count; i++ ) {
            toRemove.add(this.at(this.size() - 1 - i));
        }
        this.removeAll(toRemove);
        return this;
    }

    @Override
    public Vars<Object> popLast(int count) {
        Vars<Object> toRemove = Vars.of(this.type());
        for ( int i = 0; i < count; i++ ) {
            toRemove.add(this.at(this.size() - 1 - i));
        }
        this.removeAll(toRemove);
        return toRemove;
    }

    @Override
    public Vars<Object> removeFirst(int count) {
        Vars<Object> toRemove = Vars.of(this.type());
        for ( int i = 0; i < count; i++ ) {
            toRemove.add(this.at(i));
        }
        this.removeAll(toRemove);
        return this;
    }

    @Override
    public Vars<Object> popFirst(int count) {
        Vars<Object> toRemove = Vars.of(this.type());
        for ( int i = 0; i < count; i++ ) {
            toRemove.add(this.at(i));
        }
        this.removeAll(toRemove);
        return toRemove;
    }

    @Override
    public Vars<Object> removeAll( Vals<Object> vars ) {
        for ( Object o : vars ) _removeAt(firstIndexOf(o));
        return this;
    }

    @Override
    public Vars<Object> clear()
    {
        if ( !_isEager )
            throw new UnsupportedOperationException("Transactional modification of lists (intermediate tables) is not supported yet.");

        /*
            We need to delete all rows from the intermediate table that contain the id
            of the left model.
        */
        String query = "DELETE FROM " + intermediateTable.getTableName() + " WHERE " + thisTableIdColumn + " = ?";
        List<Object> params = List.of(id);
        db._db._update(query, params);
        ids.clear();
        return this;
    }

    @Override
    public void sort(Comparator<Object> comparator) {
        throw new UnsupportedOperationException("Not supported yet."); // How to sort on a database?
    }

    @Override
    public Vars<Object> makeDistinct() {
        throw new UnsupportedOperationException("Not supported yet."); // How to make distinct on a database?
    }

    @Override
    public Vars<Object> reversed() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Observable subscribe(Observer listener) {
        throw new IllegalStateException(); // TODO
    }

    @Override
    public Observable unsubscribe(Subscriber listener) {
        throw new IllegalStateException(); // TODO
    }

    @Override
    public void unsubscribeAll() {
        throw new IllegalStateException(); // TODO
    }
}
