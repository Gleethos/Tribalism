package dal.impl;

import org.jspecify.annotations.NullMarked;

@NullMarked
enum FieldKind {
    ID,
    PRIMITIVE,
    FOREIGN_KEY,
    INTERMEDIATE_TABLE
}
