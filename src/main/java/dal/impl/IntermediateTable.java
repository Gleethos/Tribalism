package dal.impl;

import dal.api.DataBaseEntity;
import org.jspecify.annotations.NullMarked;
import sprouts.Tuple;

import java.util.Objects;

@NullMarked
record IntermediateTable(
    EntityTableField entityField
) implements EntityTable {

    @Override
    public String getTableName() {
        return AbstractDataBase._nameFromClass(entityField.ownerModelClass()) + "__" + entityField.name() + INTER_TABLE_POSTFIX;
    }

    @Override
    public Tuple<EntityTableField> getFields() {
        return Tuple.of(EntityTableField.class);
    }

    @Override
    public Tuple<Class<? extends DataBaseEntity>> getReferencedModels() {
        Class<?> thisTableClass = entityField.ownerModelClass();
        Class<?> otherTableClass = entityField.itemType();
        Objects.requireNonNull(thisTableClass);
        Objects.requireNonNull(otherTableClass);
        return ((Tuple)Tuple.of(Class.class)).addAll((Class<? extends DataBaseEntity>) thisTableClass, (Class<? extends DataBaseEntity>) otherTableClass);
    }

    @Override
    public String createTableStatement() {
            /*
                Simple:
                - id
                - foreign_key pointing to the model table of the model to which the list belongs
                - foreign_key pointing to the model of the property type of the list
             */
        Class<?> thisTableClass = entityField.ownerModelClass();
        Class<?> otherTableClass = entityField.itemType();
        String thisTable = AbstractDataBase._tableNameFromClass(thisTableClass);
        String otherTable = AbstractDataBase._tableNameFromClass(otherTableClass);
        return "CREATE TABLE " + getTableName() + " (\n" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                "    " + EntityTable.INTER_LEFT_FK_PREFIX + thisTable + EntityTable.INTER_FK_POSTFIX + " INTEGER NOT NULL,\n" +
                "    " + EntityTable.INTER_RIGHT_FK_PREFIX + otherTable + EntityTable.INTER_FK_POSTFIX + " INTEGER NOT NULL,\n" +
                "    FOREIGN KEY (" + EntityTable.INTER_LEFT_FK_PREFIX + thisTable + EntityTable.INTER_FK_POSTFIX + ") REFERENCES " + thisTable + "(id),\n" +
                "    FOREIGN KEY (" + EntityTable.INTER_RIGHT_FK_PREFIX + otherTable + EntityTable.INTER_FK_POSTFIX + ") REFERENCES " + otherTable + "(id)\n" +
                ");";
    }

    @Override
    public Tuple<Object> getDefaultValues() {
        throw new UnsupportedOperationException("An intermediate table does not have default values");
    }

}
