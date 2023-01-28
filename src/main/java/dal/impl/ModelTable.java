package dal.impl;

import dal.api.Model;

import java.util.List;
import java.util.Optional;

interface ModelTable
{
    String INTER_TABLE_POSTFIX = "_list_table";
    String INTER_LEFT_FK_PREFIX = "fk_self_";
    String INTER_RIGHT_FK_PREFIX = "fk_";
    String INTER_FK_POSTFIX = "_id";
    String FK_POSTFIX = "_id";
    String FK_PREFIX = "fk_";


    String getTableName();

    List<TableField> getFields();

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

    default TableField getField(Class<?> propertyType) {
        for (TableField field : getFields()) {
            if (field.getPropType().equals(propertyType))
                return field;
        }
        throw new IllegalArgumentException("No field with type " + propertyType.getName() + " found!");
    }

    List<Class<? extends Model<?>>> getReferencedModels();

    default Optional<Class<? extends Model<?>>> getModelInterface() {
        return Optional.empty();
    }

    String createTableStatement();

    List<Object> getDefaultValues();

}
