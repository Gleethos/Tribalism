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
            return switch (this) {
                case Primitive ignored -> new VarOf.Primitive((Class) Var.class, item());
                case Value ignored -> new VarOf.Value((Class) Var.class, (Class) item());
                case Model ignored -> new VarOf.Model((Class) Var.class, (Class) item());
            };
        }
    }

    record Primitive(Class<?> item) implements FieldType {}
    record Value(Class<? extends Value> item) implements FieldType {}
    record Tuple(Class<? extends Value> item) implements FieldType {}

    Class<?> item();

    default @Nullable Class<?> wrapperType() {
        return switch (this) {
            case Value ignored -> null;
            case VarOf.Value value -> value.var();
            case VarOf.Model model -> model.var();
            case VarOf.Primitive primitive -> primitive.var();
            case Primitive ignored -> null;
            case VarsOf.Primitive primitive -> primitive.vars();
            case VarsOf.Value value -> value.vars();
            case VarsOf.Model model -> model.vars();
            case Tuple ignored -> null;
            case VarOf.Tuple tuple -> tuple.var();
            case VarOf.Id id -> id.var();
        };
    }

    default FieldKind kind() {
        return switch (this) {
            case VarOf.Id ignored -> FieldKind.ID;
            case Value ignored -> FieldKind.FOREIGN_KEY;
            case VarOf.Value ignored -> FieldKind.FOREIGN_KEY;
            case VarOf.Model ignored -> FieldKind.FOREIGN_KEY;
            case VarOf.Primitive ignored -> FieldKind.PRIMITIVE;
            case Primitive ignored -> FieldKind.PRIMITIVE;
            case VarsOf.Primitive ignored1 -> FieldKind.INTERMEDIATE_TABLE;
            case VarsOf.Value ignored -> FieldKind.INTERMEDIATE_TABLE;
            case VarsOf.Model ignored -> FieldKind.INTERMEDIATE_TABLE;
            case Tuple ignored -> FieldKind.INTERMEDIATE_TABLE;
            case VarOf.Tuple ignored -> FieldKind.INTERMEDIATE_TABLE;
        };
    }
}
