package dal.impl;

import dal.api.Model;
import org.jspecify.annotations.Nullable;
import sprouts.Var;
import sprouts.Vars;

sealed interface FieldType {
    record VarOfId(Class<? extends Var<?>> varType, Class<?> itemType) implements FieldType {}
    record VarOfPrimitive(Class<? extends Var<?>> varType, Class<?> itemType) implements FieldType {}
    record VarOfValue(Class<? extends Var<?>> varType, Class<? extends Value> itemType) implements FieldType {}
    record VarOfTuple(Class<? extends Var<?>> varType, Class<? extends Value> itemType) implements FieldType {}
    record VarOfModel(Class<? extends Var<?>> varType, Class<? extends Model<?>> itemType) implements FieldType {}

    record VarsOfPrimitive(Class<? extends Vars<?>> varsType, Class<?> itemType) implements FieldType {}
    record VarsOfValue(Class<? extends Vars<?>> varsType, Class<? extends Value> itemType) implements FieldType {}
    record VarsOfModel(Class<? extends Vars<?>> varsType, Class<? extends Model<?>> itemType) implements FieldType {}

    record Primitive(Class<?> itemType) implements FieldType {}
    record Value(Class<? extends Value> itemType) implements FieldType {}
    record Tuple(Class<? extends Value> itemType) implements FieldType {}

    Class<?> itemType();

    default @Nullable Class<?> wrapperType() {
        if (this instanceof Value)
            return null;
        if (this instanceof VarOfValue)
            return ((VarOfValue) this).varType();
        if (this instanceof VarOfModel)
            return ((VarOfModel) this).varType();
        if (this instanceof VarOfPrimitive)
            return ((VarOfPrimitive) this).varType();
        if (this instanceof Primitive)
            return null;
        if (this instanceof VarsOfPrimitive)
            return ((VarsOfPrimitive) this).varsType();
        if (this instanceof VarsOfValue)
            return ((VarsOfValue) this).varsType();
        if (this instanceof VarsOfModel)
            return ((VarsOfModel) this).varsType();
        if (this instanceof Tuple)
            return null;
        if (this instanceof VarOfTuple)
            return ((VarOfTuple) this).varType();
        if ( this instanceof VarOfId )
            return ((VarOfId) this).varType();
        throw new RuntimeException("unknown kind of field: " + this);
    }

    default FieldKind kind() {
        if (this instanceof VarOfId)
            return FieldKind.ID;
        if (this instanceof Value)
            return FieldKind.FOREIGN_KEY;
        if (this instanceof VarOfValue)
            return FieldKind.FOREIGN_KEY;
        if (this instanceof VarOfModel)
            return FieldKind.FOREIGN_KEY;
        if (this instanceof VarOfPrimitive)
            return FieldKind.PRIMITIVE;
        if (this instanceof Primitive)
            return FieldKind.PRIMITIVE;
        if (this instanceof VarsOfPrimitive)
            return FieldKind.INTERMEDIATE_TABLE;
        if (this instanceof VarsOfValue)
            return FieldKind.INTERMEDIATE_TABLE;
        if (this instanceof VarsOfModel)
            return FieldKind.INTERMEDIATE_TABLE;
        if (this instanceof Tuple)
            return FieldKind.INTERMEDIATE_TABLE;
        if (this instanceof VarOfTuple)
            return FieldKind.INTERMEDIATE_TABLE;
        throw new RuntimeException("unknown kind of field: " + this);
    }
}
