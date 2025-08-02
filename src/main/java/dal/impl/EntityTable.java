package dal.impl;

import dal.api.DataBaseEntity;
import dal.api.Model;
import org.jspecify.annotations.NullMarked;
import sprouts.Tuple;

import java.util.Optional;

@NullMarked
sealed interface EntityTable permits ModelTable, IntermediateTable
{
    String INTER_TABLE_POSTFIX = "_list_table";
    String INTER_LEFT_FK_PREFIX = "fk_self_";
    String INTER_RIGHT_FK_PREFIX = "fk_";
    String INTER_FK_POSTFIX = "_id";
    String FK_POSTFIX = "_id";
    String FK_PREFIX = "fk_";
    String ID = "id";


    String getTableName();

    Tuple<TableField> getFields();

    default TableField getField(String name) {
        for (TableField field : getFields()) {
            if (field.isField(name))
                return field;
        }
        throw new IllegalArgumentException("No field with name " + name + " found!");
    }

    default boolean hasField(String name) {
        for ( TableField field : getFields() ) {
            if ( field.isField(name) )
                return true;
        }
        return false;
    }

    default TableField getField(Class<?> wrapperType) {
        for (TableField field : getFields()) {
            if (field.wrapperType().equals(wrapperType))
                return field;
        }
        throw new IllegalArgumentException("No field with type " + wrapperType.getName() + " found!");
    }

    Tuple<Class<? extends DataBaseEntity>> getReferencedModels();

    default Optional<Class<? extends DataBaseEntity>> entityType() {
        return Optional.empty();
    }

    String createTableStatement();

    Tuple<Object> getDefaultValues();

}
