package dal.impl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

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
 */
class PropertySelectionProxy implements InvocationHandler
{
    private final ModelTable _modelTable;
    private TableField _selection = null;

    public PropertySelectionProxy(ModelTable modelTable) {
        _modelTable = modelTable;
    }

    @Override
    public Object invoke(
            Object proxy,
            Method method,
            Object[] args
    ) throws Throwable {
        List<TableField> fields = _modelTable.getFields();
        for (TableField field : fields) {
            if (field.getMethodName().equals(method.getName())) {
                _selection = field;
                Class<?> propType = field.getPropType();
                // We return a proxy that will return the value of the property
                return java.lang.reflect.Proxy.newProxyInstance(
                        propType.getClassLoader(),
                        new Class<?>[]{propType},
                        (proxy1, method1, args1) -> {
                            throw new IllegalCallerException(
                                    "This is a selector proxy, you can't call methods on it!"
                                );
                        }
                );
            }
        }
        throw new IllegalCallerException(
                "This is a selector proxy for selecting properties only, " +
                "you can't call other methods on it!"
            );
    }

    public Optional<TableField> getSelection() { return Optional.ofNullable(_selection); }
}
