package dal.impl;

import dal.api.DataBaseEntity;
import dal.api.Value;
import org.jspecify.annotations.NullMarked;
import sprouts.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 *  A "union" table backing a <b>sum type</b>: a sealed interface that {@code extends Value} and whose
 *  permitted subtypes are the record "tips" (or, recursively, further sealed sum types).
 *  <p>
 *  A value of the sum type is stored polymorphically: the union table carries a {@code type}
 *  discriminator column plus one nullable foreign-key column per direct permitted subtype. A given
 *  union row sets exactly one of those FK columns (the one matching its discriminator) and points at
 *  the row holding the concrete value. A field whose declared type is the sealed interface is therefore
 *  an ordinary foreign key to this union table — the polymorphism is fully encapsulated here.
 *  <pre>{@code
 *    sealed interface Shape extends Value permits Circle, Rectangle {}
 *    // ->  Shape_table(id, usages, type, fk_Circle_id -> Circle_table, fk_Rectangle_id -> Rectangle_table)
 *  }</pre>
 */
@NullMarked
record SumTable(
    Tuple<EntityTableField> fields,
    Class<? extends Value> sealedType,
    Tuple<Class<? extends Value>> permittedSubtypes
) implements EntityTable
{
    public static final String USAGE_FIELD_COUNTER = "usages";
    public static final String TYPE_FIELD = "type";

    static SumTable of( Class<? extends Value> sealedType ) {
        if ( !sealedType.isInterface() || !sealedType.isSealed() )
            throw new IllegalArgumentException(
                    "The sum type '" + sealedType.getName() + "' must be a sealed interface."
            );
        Class<?>[] permits = sealedType.getPermittedSubclasses();
        if ( permits == null || permits.length == 0 )
            throw new IllegalArgumentException(
                    "The sealed type '" + sealedType.getName() + "' has no permitted subtypes."
            );

        List<Class<? extends Value>> subtypes = new ArrayList<>();
        List<EntityTableField> fields = new ArrayList<>();
        fields.add(new EntityTableField(EntityTable.ID,           sealedType, new FieldType.Primitive(Long.class),   false));
        fields.add(new EntityTableField(USAGE_FIELD_COUNTER,      sealedType, new FieldType.Primitive(Long.class),   false));
        fields.add(new EntityTableField(TYPE_FIELD,               sealedType, new FieldType.Primitive(String.class), false));

        List<String> seenSimpleNames = new ArrayList<>();
        for ( Class<?> permit : permits ) {
            if ( !Value.class.isAssignableFrom(permit) )
                throw new IllegalArgumentException(
                        "Permitted subtype '" + permit.getName() + "' of sealed value type '" +
                        sealedType.getName() + "' must itself implement " + Value.class.getName() + "."
                );
            if ( !permit.isRecord() && !(permit.isInterface() && permit.isSealed()) )
                throw new IllegalArgumentException(
                        "Permitted subtype '" + permit.getName() + "' of sealed value type '" +
                        sealedType.getName() + "' must be a record or a (nested) sealed value interface."
                );
            if ( seenSimpleNames.contains(permit.getSimpleName()) )
                throw new IllegalArgumentException(
                        "Two permitted subtypes of '" + sealedType.getName() + "' share the simple name '" +
                        permit.getSimpleName() + "'; sum-type subtypes must have distinct simple names."
                );
            seenSimpleNames.add(permit.getSimpleName());
            @SuppressWarnings("unchecked")
            Class<? extends Value> sub = (Class<? extends Value>) permit;
            subtypes.add(sub);
            fields.add(new EntityTableField(permit.getSimpleName(), sealedType, new FieldType.Value((Class) sub), false));
        }
        return new SumTable(
                Tuple.of(EntityTableField.class, fields),
                sealedType,
                Tuple.of(Class.class, (List) subtypes)
        );
    }

    /** The direct permitted subtype that the given concrete value is an instance of. */
    Class<? extends Value> directPermitFor( Value value ) {
        for ( Class<? extends Value> sub : permittedSubtypes )
            if ( sub.isInstance(value) )
                return sub;
        throw new IllegalArgumentException(
                "Value of type '" + value.getClass().getName() + "' is not a permitted subtype of sealed type '" +
                sealedType.getName() + "' (permitted: " + _permitNames() + ")."
        );
    }

    /** Resolves a stored discriminator back to its direct permitted subtype. */
    Optional<Class<? extends Value>> permitByDiscriminator( String discriminator ) {
        for ( Class<? extends Value> sub : permittedSubtypes )
            if ( discriminatorOf(sub).equals(discriminator) )
                return Optional.of(sub);
        return Optional.empty();
    }

    boolean isPermitted( Class<?> subtype ) {
        for ( Class<? extends Value> sub : permittedSubtypes )
            if ( sub.equals(subtype) )
                return true;
        return false;
    }

    /** The stored discriminator string for a permitted subtype (its fully-qualified name). */
    static String discriminatorOf( Class<?> permit ) { return permit.getName(); }

    /** The FK column name in this union table that points at the given permitted subtype. */
    String fkColumnFor( Class<?> permit ) {
        return EntityTable.FK_PREFIX + permit.getSimpleName() + EntityTable.FK_POSTFIX;
    }

    private String _permitNames() {
        List<String> names = new ArrayList<>();
        for ( Class<? extends Value> s : permittedSubtypes ) names.add(s.getSimpleName());
        return String.join(", ", names);
    }

    @Override public String getTableName() { return BasicSQLiteDataBase._tableNameFromClass(sealedType); }

    @Override public Tuple<EntityTableField> getFields() { return fields; }

    @Override
    public Tuple<Class<? extends DataBaseEntity>> getReferencedModels() {
        List<Class<? extends DataBaseEntity>> referenced = new ArrayList<>();
        for ( Class<? extends Value> sub : permittedSubtypes )
            referenced.add(sub);
        return ((Tuple) Tuple.of(Class.class)).addAll(referenced);
    }

    @Override public Optional<Class<? extends DataBaseEntity>> entityType() { return Optional.of(sealedType); }

    @Override
    public String createTableStatement() {
        StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(getTableName()).append(" (");
        for ( EntityTableField field : fields )
            field.asSqlColumn().ifPresent(col -> sb.append(col).append(", "));
        sb.delete(sb.length() - 2, sb.length());
        return sb.append(");").toString();
    }

    @Override
    public Tuple<Object> getDefaultValues() {
        List<Object> defaults = new ArrayList<>();
        for ( EntityTableField field : fields )
            defaults.add(field.isForeignKey() ? null : field.getDefaultValue());
        return Tuple.ofNullable(Object.class, defaults);
    }
}
