package dal.impl;

import org.jspecify.annotations.Nullable;
import sprouts.Var;
import sprouts.Vars;

sealed interface FieldType {
    sealed interface VarOf {
        record Id(Class<? extends Var<?>> var, Class<?> item) implements FieldType, VarOf {}
        record Primitive(Class<? extends Var<?>> var, Class<?> item) implements FieldType, VarOf {}
        record Value(Class<? extends Var<?>> var, Class<? extends dal.api.Value> item) implements FieldType, VarOf {}
        record Tuple(Class<? extends Var<?>> var, Class<? extends dal.api.Value> item) implements FieldType, VarOf {}
        record Model(Class<? extends Var<?>> var, Class<? extends dal.api.Model<?>> item) implements FieldType, VarOf {}

        Class<?> var();
        Class<?> item();
    }
    sealed interface VarsOf {
        record Primitive(Class<? extends Vars<?>> vars, Class<?> item) implements FieldType, VarsOf {}
        record Value(Class<? extends Vars<?>> vars, Class<? extends dal.api.Value> item) implements FieldType, VarsOf {}
        record Model(Class<? extends Vars<?>> vars, Class<? extends dal.api.Model<?>> item) implements FieldType, VarsOf {}

        Class<?> vars();
        Class<?> item();
        default VarOf varOf() {
            if (this instanceof VarsOf.Primitive)
                return new VarOf.Primitive((Class)Var.class, item());
            else if (this instanceof VarsOf.Value)
                return new VarOf.Value((Class)Var.class, (Class)item());
            else if (this instanceof VarsOf.Model)
                return new VarOf.Model((Class)Var.class, (Class)item());
            throw new RuntimeException("unknown kind of vars: " + this);
        }
    }

    record Primitive(Class<?> item) implements FieldType {}
    record Value(Class<? extends Value> item) implements FieldType {}
    record Tuple(Class<? extends Value> item) implements FieldType {}

    Class<?> item();

    default @Nullable Class<?> wrapperType() {
        if (this instanceof Value)
            return null;
        if (this instanceof VarOf.Value)
            return ((VarOf.Value) this).var();
        if (this instanceof VarOf.Model)
            return ((VarOf.Model) this).var();
        if (this instanceof VarOf.Primitive)
            return ((VarOf.Primitive) this).var();
        if (this instanceof Primitive)
            return null;
        if (this instanceof VarsOf.Primitive)
            return ((VarsOf.Primitive) this).vars();
        if (this instanceof VarsOf.Value)
            return ((VarsOf.Value) this).vars();
        if (this instanceof VarsOf.Model)
            return ((VarsOf.Model) this).vars();
        if (this instanceof Tuple)
            return null;
        if (this instanceof VarOf.Tuple)
            return ((VarOf.Tuple) this).var();
        if ( this instanceof VarOf.Id)
            return ((VarOf.Id) this).var();
        throw new RuntimeException("unknown kind of field: " + this);
    }

    default FieldKind kind() {
        if (this instanceof VarOf.Id)
            return FieldKind.ID;
        if (this instanceof Value)
            return FieldKind.FOREIGN_KEY;
        if (this instanceof VarOf.Value)
            return FieldKind.FOREIGN_KEY;
        if (this instanceof VarOf.Model)
            return FieldKind.FOREIGN_KEY;
        if (this instanceof VarOf.Primitive)
            return FieldKind.PRIMITIVE;
        if (this instanceof Primitive)
            return FieldKind.PRIMITIVE;
        if (this instanceof VarsOf.Primitive)
            return FieldKind.INTERMEDIATE_TABLE;
        if (this instanceof VarsOf.Value)
            return FieldKind.INTERMEDIATE_TABLE;
        if (this instanceof VarsOf.Model)
            return FieldKind.INTERMEDIATE_TABLE;
        if (this instanceof Tuple)
            return FieldKind.INTERMEDIATE_TABLE;
        if (this instanceof VarOf.Tuple)
            return FieldKind.INTERMEDIATE_TABLE;
        throw new RuntimeException("unknown kind of field: " + this);
    }
}
