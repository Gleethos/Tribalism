package dal.impl;

import dal.api.Model;
import sprouts.*;

import java.util.*;

public class ModelProperties implements Vars<Object>
{
    private final SQLiteDataBase db;
    private final List<Integer> ids;
    private final int id; // The id of the model to which the properties belong
    private final ModelTable intermediateTable;
    private final String otherTable;
    private final Class<?> propertyValueType;
    private final String otherTableIdColumn;
    private final String thisTableIdColumn;
    private final boolean _isEager;

    public ModelProperties(
            SQLiteDataBase db,
            Class<?> ownerModelClass,
            Class<?> propertyValueType,
            ModelTable intermediateTable,
            int id,
            boolean isEager
    ) {
        this.db = db;
        this.propertyValueType = propertyValueType;
        this.intermediateTable = intermediateTable;
        this.id = id;
        _isEager = isEager;

        // We need to find the name of the column that contains the ids of the models
        // that are referenced by the intermediate table:
        this.otherTable = AbstractDataBase._tableNameFromClass(propertyValueType);
        this.otherTableIdColumn = ModelTable.INTER_RIGHT_FK_PREFIX + otherTable + ModelTable.INTER_FK_POSTFIX;
        this.thisTableIdColumn = ModelTable.INTER_LEFT_FK_PREFIX + AbstractDataBase._tableNameFromClass(ownerModelClass) + ModelTable.INTER_FK_POSTFIX;
        String query = "SELECT " + otherTableIdColumn + " FROM " + intermediateTable.getTableName() + " WHERE " + thisTableIdColumn + " = ?";

        List<Object> param = Collections.singletonList(id);
        Map<String, List<Object>> result = db._query(query, param);

        if ( result.size() == 0 )
            result.put(otherTableIdColumn, new ArrayList<>());

        // The column should be named after the id column of the other table:
        if ( !result.containsKey(otherTableIdColumn) )
            throw new IllegalStateException("The column should be named after the id column of the other table");
        // The column should contain a list of ids:
        List<Object> found = result.get(otherTableIdColumn);
        this.ids = new ArrayList<>(found.stream().map(o -> (Integer) o).toList());
    }

    private Model<?> _select( int id ) {
        // We need to get the model from the database:
        Class<Model> propertyValueType = (Class<Model>) this.propertyValueType;
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

    @Override public Class<Object> type() { return (Class<Object>) propertyValueType; }

    @Override public int size() { return ids.size(); }

    @Override
    public Var<Object> at(int index) {
        return new ModelProperty(
                db,
                ids.get(index),
                ModelTable.INTER_RIGHT_FK_PREFIX + otherTable + ModelTable.INTER_FK_POSTFIX,
                intermediateTable.getTableName(),
                propertyValueType,
                false,
                _isEager
            );
    }

    @Override
    public Vals<Object> onChange(Action<ValsDelegate<Object>> action) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Vals<Object> fireChange() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Vars<Object> removeAt(int index)
    {
        if ( !_isEager )
            throw new UnsupportedOperationException("Transactional modification of lists (intermediate tables) is not supported yet.");
        _removeAt(index);
        return this;
    }

    private void _removeAt(int index) {
        /*
            Basically all we need to do is delete a row from the intermediate table!
            Which row? The one that contains the id of the left model and the id of the
            model at the given index.
        */
        int leftId = id;
        int rightId = ids.get(index);
        String query = "DELETE FROM " + intermediateTable.getTableName() + " " +
                "WHERE " + thisTableIdColumn + " = ? AND " + otherTableIdColumn + " = ?";
        List<Object> params = List.of(leftId, rightId);
        db._update(query, params);
        ids.remove(index);
    }

    @Override
    public Vars<Object> addAt( int index, Var<Object> var )
    {
        if ( !_isEager )
            throw new UnsupportedOperationException("Transactional modification of lists (intermediate tables) is not supported yet.");

        Objects.requireNonNull(var);
        // First let's verify the type:
        if ( !propertyValueType.isAssignableFrom(var.type()) )
            throw new IllegalArgumentException("The type of the var is not the same as the type of the property");

        /*
            We need to insert a row into the intermediate table! Basic stuff...
        */
        Object o = var.get();
        int leftId = id;
        int rightId = ((Model) o).id().get();
        String query = "INSERT INTO " + intermediateTable.getTableName() + " " +
                "(" + thisTableIdColumn + ", " + otherTableIdColumn + ") " +
                "VALUES (?, ?)";
        List<Object> params = List.of(leftId, rightId);
        db._update(query, params);
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
        int leftId = id;
        int rightId = (Integer) var.get();
        int oldRightId = ids.get(index);

        String update = "UPDATE " + intermediateTable.getTableName() +
                " SET " + otherTableIdColumn +
                " = ? WHERE id = ?";

        List<Object> params = List.of(rightId, oldRightId);
        db._update(update, params);
        ids.set(index, rightId);
        return this;
    }

    @Override
    public Vars<Object> retainAll(Vars<Object> vars) {
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
    public Vars<Object> removeAll( Vars<Object> vars ) {
        for ( Object o : vars ) _removeAt(indexOf(o));
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
        db._update(query, params);
        ids.clear();
        return this;
    }

    @Override
    public void sort(Comparator<Object> comparator) {
        throw new UnsupportedOperationException("Not supported yet."); // How to sort on a database?
    }

    @Override
    public void makeDistinct() {
        throw new UnsupportedOperationException("Not supported yet."); // How to make distinct on a database?
    }

    @Override
    public Vars<Object> revert() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
