package dal.impl;

import dal.api.Value;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 *  Shared logic for turning a resolved <em>component chain</em> — the ordered list of value-field
 *  names a selector navigates through, e.g. {@code [address, postalCode]} — into a renderable
 *  {@link Selection.Zoom}. Used both by {@link ZoomLensResolver} (which derives the chain from a
 *  model default method via the ClassFile API) and by {@link NestedSelectionResolver} (which derives
 *  it by executing navigation lambdas against a probe value).
 *  <p>
 *  Validation is uniform: the root must be a {@code Value} foreign key, every intermediate hop must
 *  be a {@code Value} foreign key, and the final hop must be a primitive/queryable column.
 */
@NullMarked
final class ZoomPaths {

    private ZoomPaths() {}

    static Selection.Zoom build(
        EntityTableField rootField,
        List<String> componentNames,
        EntityRegistry registry,
        String what
    ) {
        if ( !rootField.isForeignKey() || !Value.class.isAssignableFrom(rootField.type().item()) )
            throw fail(what, "the root property '" + rootField.baseName() + "' is not a Value-typed field; " +
                    "only fields holding a Value can be zoomed into");
        if ( componentNames.isEmpty() )
            throw fail(what, "it does not select any nested value field");

        Class<?> currentType = rootField.type().item();
        ValueTable vt = valueTable(registry, currentType, what);
        List<String> tables = new ArrayList<>();
        List<String> links = new ArrayList<>();
        tables.add(vt.getTableName());
        @Nullable String leafColumn = null;

        for ( int i = 0; i < componentNames.size(); i++ ) {
            String name = componentNames.get(i);
            EntityTableField field = findField(vt, name);
            if ( field == null )
                throw fail(what, "value type '" + currentType.getName() + "' has no field named '" + name + "'");
            boolean last = ( i == componentNames.size() - 1 );
            if ( !last ) {
                if ( !field.isForeignKey() || !Value.class.isAssignableFrom(field.type().item()) )
                    throw fail(what, "the intermediate field '" + name + "' is not a Value-typed field, " +
                            "so it cannot be nested into further");
                links.add(field.name());
                currentType = field.type().item();
                vt = valueTable(registry, currentType, what);
                tables.add(vt.getTableName());
            } else {
                if ( !BasicSQLiteDataBase._isBasicDataType(field.type().item()) )
                    throw fail(what, "the final field '" + name + "' is not a primitive/queryable column " +
                            "(its type is '" + field.type().item().getName() + "'); only leaf primitives can be queried");
                leafColumn = field.name();
            }
        }
        return new Selection.Zoom(rootField.name(), List.copyOf(tables), List.copyOf(links),
                java.util.Objects.requireNonNull(leafColumn));
    }

    static @Nullable EntityTableField findField(EntityTable table, String baseName) {
        for ( EntityTableField field : table.getFields() )
            if ( field.baseName().equals(baseName) )
                return field;
        return null;
    }

    static ValueTable valueTable(EntityRegistry registry, Class<?> valueType, String what) {
        @SuppressWarnings("unchecked")
        var vt = registry.getValueTable((Class<? extends Value>) valueType).orElse(null);
        if ( vt == null )
            throw fail(what, "no value table is registered for '" + valueType.getName() +
                    "' (pass it to createTablesFor(..))");
        return vt;
    }

    static IllegalArgumentException fail(String what, String reason) {
        return new IllegalArgumentException(
                "Cannot use '" + what + "' as a query property selector, because " + reason + ".\n" +
                "A nested/zoom selector must resolve to a pure chain of value-field accessors ending at a " +
                "primitive value field (e.g. account -> account.user().address().postalCode())."
        );
    }
}
