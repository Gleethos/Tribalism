package dal.impl;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import sprouts.Val;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *  This proxy delegates a {@link dal.api.Model} sub-interface
 *  to give a user the ability to select a particular {@link sprouts.Val} or {@link sprouts.Var} property
 *  defined in the sub-interface as part of the {@link dal.api.DataBase} API.
 *  <p>
 *  A typical example would be the {@link dal.api.Where#where(Function)} method, which will
 *  expose a "selector model" to the user that allows to select a property of the model
 *  by using a method reference.
 *  <p>
 *  This might look something like this:
 *  <pre>{@code
 *    var foods = db.select(Food.class)
 *                  .where(Food::name)
 *                  .is("Chana Masala")
 *                  .asList()
 *  }</pre>
 *  Besides plain property getters, the proxy also understands <b>zoom lenses</b>: default methods
 *  that zoom into a {@link dal.api.Value} held by the model (see {@link ZoomLensResolver}). These are
 *  resolved by statically inspecting the default method's bytecode.
 */
@NullMarked
final class PropertySelectionProxy implements InvocationHandler
{
    private final EntityTable _modelTable;
    private final EntityRegistry _registry;
    private @Nullable Selection _selection = null;

    public PropertySelectionProxy(EntityTable modelTable, EntityRegistry registry) {
        _modelTable = modelTable;
        _registry = registry;
    }

    @Override
    public Object invoke(
            Object proxy,
            Method method,
            Object[] args
    ) throws Throwable {
        // 1. A plain property getter directly mapped to a column of this model/value table:
        for (EntityTableField field : _modelTable.getFields()) {
            if (field.baseName().equals(method.getName())) {
                _selection = new Selection.Flat(field);
                Class<?> propType = field.type().wrapperType();
                if ( propType == null )
                    throw new IllegalStateException(
                            "Cannot create a property proxy for a field that does not have a wrapper type."
                        );
                return _selectorProxyFor(propType, field.baseName());
            }
        }
        // 2. A "zoom lens": a default method that zooms into a Value field of the model.
        if ( _modelTable instanceof ModelTable modelTable && method.isDefault() && _returnsProperty(method) ) {
            _selection = ZoomLensResolver.resolve(method, modelTable, _registry);
            return _selectorProxyFor(method.getReturnType(), method.getName());
        }
        // 3. Anything else is not a valid selection:
        throw new IllegalCallerException(
                "Illegal selection '" + _describeCall(_modelInterface(), method, args) + "' on a property selector for '" + _modelDescription() + "'. \n" +
                "A property selector only supports selecting one of the model's persisted properties, " +
                "but '" + method.getName() + "' is not one of them. \n" +
                "Selectable properties are: " + _selectableProperties() + ". \n" +
                ( method.isDefault()
                    ? "Note that '" + method.getName() + "' is a default method, but it could not be resolved as a zoom " +
                      "lens into a Value field (it must return a Val/Var and the model must be a regular model table). "
                    : "Make sure you select a property using a method reference like 'MyModel::someProperty'. " )
            );
    }

    public Optional<Selection> getSelection() { return Optional.ofNullable(_selection); }

    private static boolean _returnsProperty(Method method) {
        return Val.class.isAssignableFrom(method.getReturnType()); // Val or its subtype Var
    }

    /** A stand-in for the selected property: it only marks the selection; calling methods on it is illegal. */
    private Object _selectorProxyFor(Class<?> propType, String propertyName) {
        return java.lang.reflect.Proxy.newProxyInstance(
                propType.getClassLoader(),
                new Class<?>[]{propType},
                (proxy1, method1, args1) -> {
                    throw new IllegalCallerException(
                            "Illegal call to '" + _describeCall(propType, method1, args1) + "' on a property selector. \n" +
                            "A property selector only records *which* property of '" + _modelDescription() + "' " +
                            "you are selecting (here: the '" + propertyName + "' property of type '" + propType.getName() + "'); \n" +
                            "it is not a live property, so no methods (such as 'get()', 'set(..)' or 'zoomTo(..)') may be invoked on it. \n" +
                            "Use a plain method reference like 'MyModel::" + propertyName + "' instead of a lambda that calls into the property."
                        );
                }
        );
    }

    /** A human-readable signature of the invoked method, including the receiver type and argument types. */
    private static String _describeCall(Class<?> receiverType, Method method, Object @Nullable [] args) {
        StringJoiner params = new StringJoiner(", ", "(", ")");
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            String declared = paramTypes[i].getSimpleName();
            Object arg = (args != null && i < args.length) ? args[i] : null;
            String actual = (arg == null) ? "null" : arg.getClass().getSimpleName();
            params.add( declared.equals(actual) || arg == null ? declared : declared + " <" + actual + ">" );
        }
        return receiverType.getSimpleName() + "." + method.getName() + params +
               " : " + method.getReturnType().getSimpleName();
    }

    private Class<?> _modelInterface() {
        Optional<? extends Class<?>> type = _modelTable.entityType();
        return type.isPresent() ? type.get() : Object.class;
    }

    private String _modelDescription() {
        return _modelTable.entityType().map(Class::getName).orElse(_modelTable.getTableName());
    }

    private String _selectableProperties() {
        return _modelTable.getFields().stream()
                .map(EntityTableField::baseName)
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
