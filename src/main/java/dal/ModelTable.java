package dal;

import dal.api.Model;

import java.util.List;
import java.util.Optional;

interface ModelTable {

    String getName();

    List<ModelField> getFields();

    default ModelField getField(String name) {
        for (ModelField field : getFields()) {
            if (field.isField(name))
                return field;
        }
        throw new IllegalArgumentException("No field with name " + name + " found!");
    }

    default ModelField getField(Class<?> propertyType) {
        for (ModelField field : getFields()) {
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

    boolean isIntermediateTable();
}
