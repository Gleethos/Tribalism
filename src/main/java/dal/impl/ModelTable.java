package dal.impl;

import dal.api.DataBaseEntity;
import dal.api.Model;
import org.jspecify.annotations.NullMarked;
import sprouts.Tuple;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

@NullMarked
record ModelTable(
    Tuple<EntityTableField> fields,
    Class<? extends Model<?>> modelInterface
) implements EntityTable
{
    static ModelTable of(
        Class<? extends Model<?>> modelInterface,
        Tuple<Class<? extends DataBaseEntity>> otherModels
    ) {
        // We expect something like this:
        /*
            public interface Address extends Model<Address> {
                interface Id extends Val<Integer> {} // Optional! But has to be immutable!
                interface Street extends Var<String> {}
                interface Number extends Var<Integer> {}
                interface ZipCode extends Var<Integer> {}
                interface City extends Var<String> {}
                Street street();
                Number number();
                ZipCode zipCode();
                City city();
            }
            // Note: Var is mutable and Val is immutable (only get but no set)
        */
        // First we need to check if the interface is a subclass of Model
        if (!Model.class.isAssignableFrom(modelInterface))
            throw new IllegalArgumentException(
                    "The interface " + modelInterface.getName() + " is not a subclass of " + Model.class.getName()
            );
        // Now we check if the first type parameter of the Model interface is the same as the interface itself
        // We need to find the extends clause of the interface
        if (!Model.class.equals(modelInterface)) {
            Type[] interfaces = modelInterface.getGenericInterfaces();
            if (interfaces.length != 1)
                throw new IllegalArgumentException(
                        "The interface " + modelInterface.getName() + " has more than one extends clause"
                );
            Type[] typeParameters = ((ParameterizedType) interfaces[0]).getActualTypeArguments();
            if (typeParameters.length != 1)
                throw new IllegalArgumentException(
                        "The interface " + modelInterface.getName() + " is not a subclass of " + Model.class.getName() + " with one type parameter"
                );

            if (!modelInterface.equals(typeParameters[0]))
                throw new IllegalArgumentException(
                        "The first type parameter of the interface " + modelInterface.getName() + " is +" +
                        "not the same as the interface itself."
                    );
        }
        // Now we can get the fields
        Method[] methods = modelInterface.getMethods();
        List<EntityTableField> fields = new ArrayList<>();
        for (Method method : methods) {
            // First we check if it is a default method, which we can ignore
            if (method.isDefault())
                continue;
            Method[] allowedFields = Model.class.getMethods();
            if (!Arrays.asList(allowedFields).contains(method)) {
                
                // We do not allow methods with parameters
                if (method.getParameterCount() != 0) {
                    throw new IllegalArgumentException(
                            "The method '" + method.getName() + "(..)' of the interface " + modelInterface.getName() + " has parameters" +
                                    "which is not allowed!"
                    );
                }
                // We do not allow methods with a return type of void
                if (method.getReturnType().equals(Void.TYPE))
                    throw new IllegalArgumentException(
                            "The method " + method.getName() + " of the interface " + modelInterface.getName() + " has a return type of void"
                    );
                // The method is not allowed to be called "id" because that is reserved for the id field:
                if (method.getDeclaringClass().equals(modelInterface) && method.getName().equals(EntityTable.ID))
                    throw new IllegalArgumentException(
                        "The method " + method.getName() + " of the interface " + modelInterface.getName() + " is " +
                        "not allowed to be called \""+ EntityTable.ID+"\", " +
                        "because that name is already reserved for the internal table entry id!"
                    );
                fields.add(EntityTableField.of(method, modelInterface, otherModels));
            }
        }
        // Now we add the id field
        Class<Model> modelClass = Model.class;
        try {
            Method idMethod = modelClass.getMethod(EntityTable.ID);
            fields.add(0, EntityTableField.of(idMethod, modelInterface, otherModels));
        } catch (NoSuchMethodException | SecurityException e) {
            throw new RuntimeException(e);
        }
        // Now we prepare the sorted fields list
        List<EntityTableField> sortedFields = sort(fields);
        return new ModelTable(Tuple.of(EntityTableField.class, sortedFields), modelInterface);
    }

    public static List<EntityTableField> sort(List<EntityTableField> fields) {
        // Before storing the fields as array in this object let's first sort them,
        // we do this because the methods we get through reflection are not sorted...
        // What we want is simple: The id field should be the first field and the rest should be sorted alphabetically
        // Additionally we want foreign keys to be last

        // Before all that however, let's first sort the fields by name alphabetically
        // to make sure that the initial order is always the same (making thing deterministic)
        fields.sort(Comparator.comparing(EntityTableField::name));

        // Now we prepare the sorted fields list
        List<EntityTableField> sortedFields = new ArrayList<>(fields);

        // Finally we sort the fields by kind and then by name if the kind is the same
        sortedFields.sort((field1, field2) -> {
            var firstKind = field1.getKind();
            var secondKind = field2.getKind();
            // Same kind: Sort alphabetically
            if (firstKind.equals(secondKind))
                return field1.name().compareTo(field2.name());
            // Now we have an order between the field kinds which is simply the order of the enums:
            return firstKind.compareTo(secondKind);
        });
        return sortedFields;
    }

    @Override
    public String getTableName() {
        return AbstractDataBase._tableNameFromClass(modelInterface);
    }

    @Override
    public Tuple<EntityTableField> getFields() {
        return fields;
    }

    @Override
    public Tuple<Class<? extends DataBaseEntity>> getReferencedModels() {
        List<Class<? extends DataBaseEntity>> referencedModels = new ArrayList<>();
        for (EntityTableField field : fields) {
            if (field.isForeignKey()) {
                referencedModels.add((Class<? extends DataBaseEntity>) field.type().item());
            }
        }
        return ((Tuple)Tuple.of(Class.class)).addAll(referencedModels);
    }

    @Override
    public Optional<Class<? extends DataBaseEntity>> entityType() {
        return Optional.of(modelInterface);
    }

    @Override
    public String createTableStatement() {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ");
        sb.append(getTableName());
        sb.append(" (");
        for (EntityTableField field : fields)
            field.asSqlColumn().ifPresent(col -> {
                sb.append(col);
                sb.append(", ");
            });

        sb.delete(sb.length() - 2, sb.length());
        sb.append(");");
        return sb.toString();
    }

    @Override
    public Tuple<Object> getDefaultValues() {
        List<Object> defaultValues = new ArrayList<>();
        for (EntityTableField field : fields) {
            if (field.isForeignKey()) {
                defaultValues.add(null);
            } else {
                defaultValues.add(field.getDefaultValue());
            }
        }
        return Tuple.ofNullable(Object.class, defaultValues);
    }

}
