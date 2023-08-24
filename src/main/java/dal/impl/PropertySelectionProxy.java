package dal.impl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public class PropertySelectionProxy implements InvocationHandler
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
