package dal;

import dal.api.Model;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

class BasicModelTable implements ModelTable
{
    private final ModelField[] fields;
    private final Class<? extends Model<?>> modelInterface;

    BasicModelTable(Class<? extends Model<?>> modelInterface, List<Class<? extends Model<?>>> otherModels) {
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
        // Now we check if the the first type parameter of the Model interface is the same as the interface itself
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
                        "The first type parameter of the interface " + modelInterface.getName() + " is not the same as the interface itself"
                );
        }
        // Now we can get the fields
        Method[] methods = modelInterface.getMethods();
        List<ModelField> fields = new ArrayList<>();
        for (Method method : methods) {
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
                if (method.getDeclaringClass().equals(modelInterface) && method.getName().equals("id"))
                    throw new IllegalArgumentException(
                            "The method " + method.getName() + " of the interface " + modelInterface.getName() + " is not allowed to be called \"id\", " +
                                    "because that is field is already present!"
                    );
                fields.add(new ModelField(method, modelInterface, otherModels));
            }
        }
        // Now we add the id field
        Class<Model> modelClass = Model.class;
        try {
            Method idMethod = modelClass.getMethod("id");
            fields.add(0, new ModelField(idMethod, modelInterface, otherModels));
        } catch (NoSuchMethodException | SecurityException e) {
            throw new RuntimeException(e);
        }

        this.fields = fields.toArray(new ModelField[0]);
        this.modelInterface = modelInterface;
    }

    @Override
    public String getName() {
        return AbstractDataBase._tableNameFromClass(modelInterface);
    }

    @Override
    public List<ModelField> getFields() {
        return Arrays.asList(fields);
    }

    @Override
    public List<Class<? extends Model<?>>> getReferencedModels() {
        List<Class<? extends Model<?>>> referencedModels = new ArrayList<>();
        for (ModelField field : fields) {
            if (field.isForeignKey()) {
                referencedModels.add((Class<? extends Model<?>>) field.getType());
            }
        }
        return referencedModels;
    }

    @Override
    public Optional<Class<? extends Model<?>>> getModelInterface() {
        return Optional.of(modelInterface);
    }

    @Override
    public String createTableStatement() {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ");
        sb.append(getName());
        sb.append(" (");
        for (ModelField field : fields)
            field.asSqlColumn().ifPresent(col -> {
                sb.append(col);
                sb.append(", ");
            });

        sb.delete(sb.length() - 2, sb.length());
        sb.append(");");
        return sb.toString();
    }

    @Override
    public List<Object> getDefaultValues() {
        List<Object> defaultValues = new ArrayList<>();
        for (ModelField field : fields) {
            if (field.isForeignKey()) {
                defaultValues.add(null);
            } else {
                defaultValues.add(field.getDefaultValue());
            }
        }
        return defaultValues;
    }

    @Override
    public boolean isIntermediateTable() {
        return false;
    }

}
