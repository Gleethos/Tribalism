package net;

import binding.UserContext;
import org.json.JSONArray;
import org.json.JSONObject;
import swingtree.api.mvvm.*;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class BindingUtil {

    private BindingUtil() {}


    /**
     *  Uses reflection to find all the properties of the given view model.
     * @return a list of property instances.
     */
    public static List<Val<Object>> findPropertiesInViewModel(Object vm) {
        List<Val<Object>> properties = new java.util.ArrayList<>();

        for (var field : vm.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                var value = field.get(vm);
                if ( value instanceof swingtree.api.mvvm.Val<?> val )
                    properties.add((Val<Object>) val);

            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return properties;
    }


    public static <T> Var<T> findViewModelPropById( Object vm, String id ) {
        return BindingUtil.findPropertiesInViewModel(vm)
                .stream()
                .filter( p -> p instanceof Var<?> )
                .map( p -> (Var<T>) p )
                .filter( p -> p.id().equals(id) )
                .findFirst()
                .orElseThrow(() ->
                        new RuntimeException(
                                "Could not find property with " +
                                        "id '" + id +"' in " + vm.getClass().getName() + "."
                        ));
    }


    public static void applyToViewModelPropertyById(Object vm, String id, String newValue ) {
        Var<Object> prop = BindingUtil.findViewModelPropById(vm, id);

        if ( newValue == null ) {
            if ( prop.allowsNull() )
                prop.act(null);
            else
                throw new RuntimeException(
                        "Property '" + id + "' does not allow null values, but view attempted to set it to null!"
                );

            return;
        }

        Class<?> type = prop.type();
        // Now let's convert the value to the correct type
        if ( type == String.class ) {
            prop.act(newValue);
        }
        else if ( type == Integer.class ) {
            prop.act(Integer.parseInt(newValue));
        }
        else if ( type == Double.class ) {
            prop.act(Double.parseDouble(newValue));
        }
        else if ( type == Boolean.class ) {
            prop.act(Boolean.parseBoolean(newValue));
        }
        else if ( Enum.class.isAssignableFrom(type) ) {
            prop.act(Enum.valueOf((Class<Enum>) type, newValue));
        }
        // Now on to array, first primitives and then normal object arrays:
        else {
            String[] strings = newValue.substring(1, newValue.length() - 1).split(",");
            if ( type == byte[].class ) {
                // We expect this to be an array like [1,2,3,4,5]
                String[] parts = strings;
                byte[] bytes = new byte[parts.length];
                for ( int i = 0; i < parts.length; i++ )
                    bytes[i] = Byte.parseByte(parts[i]);
                prop.act(bytes);
            }
            else if ( type == short[].class ) {
                // We expect this to be an array like [1,2,3,4,5]
                String[] parts = strings;
                short[] shorts = new short[parts.length];
                for ( int i = 0; i < parts.length; i++ )
                    shorts[i] = Short.parseShort(parts[i]);
                prop.act(shorts);
            }
            else if ( type == int[].class ) {
                // We expect this to be an array like [1,2,3,4,5]
                String[] parts = strings;
                int[] ints = new int[parts.length];
                for ( int i = 0; i < parts.length; i++ )
                    ints[i] = Integer.parseInt(parts[i]);
                prop.act(ints);
            }
            else if ( type == long[].class ) {
                // We expect this to be an array like [1,2,3,4,5]
                String[] parts = strings;
                long[] longs = new long[parts.length];
                for ( int i = 0; i < parts.length; i++ )
                    longs[i] = Long.parseLong(parts[i]);
                prop.act(longs);
            }
            else if ( type == float[].class ) {
                // We expect this to be an array like [1,2,3,4,5]
                String[] parts = strings;
                float[] floats = new float[parts.length];
                for ( int i = 0; i < parts.length; i++ )
                    floats[i] = Float.parseFloat(parts[i]);
                prop.act(floats);
            }
            else if ( type == double[].class ) {
                // We expect this to be an array like [1,2,3,4,5]
                String[] parts = strings;
                double[] doubles = new double[parts.length];
                for ( int i = 0; i < parts.length; i++ )
                    doubles[i] = Double.parseDouble(parts[i]);
                prop.act(doubles);
            }
            else if ( type == boolean[].class ) {
                // We expect this to be an array like [true,false,true]
                String[] parts = strings;
                boolean[] booleans = new boolean[parts.length];
                for ( int i = 0; i < parts.length; i++ )
                    booleans[i] = Boolean.parseBoolean(parts[i]);
                prop.act(booleans);
            }
            else if ( type == char[].class ) {
                // We expect this to be an array like [a,b,c]
                String[] parts = strings;
                char[] chars = new char[parts.length];
                for ( int i = 0; i < parts.length; i++ )
                    chars[i] = parts[i].charAt(0);
                prop.act(chars);
            }
            else if ( type == String[].class ) {
                // We expect this to be an array like ["a","b","c"]
                String[] parts = strings;
                for ( int i = 0; i < parts.length; i++ )
                    parts[i] = parts[i].substring(1, parts[i].length()-1);
                prop.act(parts);
            }
            else {
                throw new RuntimeException("Property '" + id + "' has an unsupported type '" + type.getName() + "'");
            }
        }
    }

    public static JSONObject callViewModelMethod(
            Object vm,
            JSONObject methodCallData,
            UserContext userContext
    ) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException {
        /*
            The call request should look like this:
            {
                "method":"bar",
                "args":[{"name":"xy", "value": 5}]
            }
        */
        String methodName = methodCallData.getString(Constants.METHOD_NAME);
        var args = methodCallData.getJSONArray(Constants.METHOD_ARGS);
        var methodArgs     = new Object[args.length()];
        var methodArgNames = new String[args.length()];
        var methodArgTypes = new Class[args.length()];
        for (int i = 0; i < args.length(); i++) {
            var arg = args.getJSONObject(i);
            String argName = arg.getString(Constants.METHOD_ARG_NAME);
            String argType = arg.getString(Constants.METHOD_ARG_TYPE);
            Object argValue = arg.get(Constants.PROP_VALUE);
            if ( argValue != null ) {
                String valueType = argValue.getClass().getSimpleName();
                // Lets do some type checking and try to convert the value if possible!
                if ( valueType.equals("String") ) {
                    if (argType.equals("int")||argType.equals("Integer"))
                        argValue = Integer.parseInt(argValue.toString());
                    else if (argType.equals("long")||argType.equals("Long"))
                        argValue = Long.parseLong(argValue.toString());
                    else if (argType.equals("double")||argType.equals("Double"))
                        argValue = Double.parseDouble(argValue.toString());
                    else if (argType.equals("float")||argType.equals("Float"))
                        argValue = Float.parseFloat(argValue.toString());
                    else if (argType.equals("boolean")||argType.equals("Boolean"))
                        argValue = Boolean.parseBoolean(argValue.toString());
                }
            }
            methodArgs[i] = argValue;
            methodArgNames[i] = argName;
            methodArgTypes[i] = Class.forName(argType);
        }

        Method method;
        Object result;
        if ( methodArgs.length == 0 ) {
            method = vm.getClass().getMethod(methodName);
            result = method.invoke(vm);
        } else {
            method = vm.getClass().getMethod(methodName, methodArgs[0].getClass());
            result = method.invoke(vm, methodArgs[0]);
        }
        if ( result instanceof Val<?> property ) {
            result = BindingUtil.jsonFromProperty(property, userContext);
        }
        return new JSONObject()
                .put(Constants.METHOD_NAME, methodName)
                .put(Constants.METHOD_RETURNS, result);
    }

    public static void bind(
            Object vm,
            Action<ValDelegate<Object>> observer
    ) {
        BindingUtil.findPropertiesInViewModel(vm).forEach(p -> p.onShow(observer) );
    }

    public static JSONObject toJson(Object vm, UserContext userContext) {
        JSONObject json = new JSONObject();
        for ( var property : BindingUtil.findPropertiesInViewModel(vm) )
            json.put(property.id(), BindingUtil.jsonFromProperty(property, userContext));

        JSONObject result = new JSONObject();
        result.put(Constants.PROPS, json);
        result.put(Constants.VM_ID, userContext.vmIdOf(vm).toString());
        result.put("methods", _getMethodsForViewModel(vm));
        return result;
    }

    private static JSONArray _getMethodsForViewModel(Object vm) {
        var publicMethods = new JSONArray();
        /*
            So lets say we have a class like this:
            class Foo extends AbstractViewModel {
                public long bar(int xy) { ... }
            }
            We want to extract the method signature into the json
            using reflection!
            Each entry should look something like this:
            {
                "name":"bar",
                "args":[{"name":"xy", "type":"int"}]
                "returns": "long"
            }
        */
        for ( var method : vm.getClass().getDeclaredMethods() ) {
            method.setAccessible(true);
            // first we check if the method is public
            if ( !java.lang.reflect.Modifier.isPublic(method.getModifiers()) )
                continue;

            try {
                String returnType = method.getReturnType().getSimpleName();
                String methodName = method.getName();
                var args = new JSONObject();
                for ( var param : method.getParameters() )
                    args.put(Constants.METHOD_ARG_NAME, param.getName())
                        .put(Constants.METHOD_ARG_TYPE, param.getType().getSimpleName())
                        .put(Constants.TYPE_IS_VM, Viewable.class.isAssignableFrom(method.getReturnType()));

                publicMethods.put(
                        new JSONObject()
                                .put(Constants.METHOD_NAME, methodName)
                                .put(Constants.METHOD_ARGS, args)
                                .put(Constants.METHOD_RETURNS,
                                    new JSONObject()
                                        .put(Constants.TYPE_NAME, returnType)
                                        .put(Constants.TYPE_IS_VM, Viewable.class.isAssignableFrom(method.getReturnType()))
                                )
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return publicMethods;
    }


    public static JSONObject jsonFromProperty(
        Val<?> property,
        UserContext userContext
    ) {
        Class<?> type = property.type();
        List<String> knownStates = new ArrayList<>();
        if ( Enum.class.isAssignableFrom(type) ) {
            for ( var state : type.getEnumConstants() )
                knownStates.add(((Enum)state).name());
        }
        JSONObject json = new JSONObject();
        json.put(Constants.PROP_NAME, property.id());
        json.put(Constants.PROP_VALUE, toJsonCompatibleValueFromProperty(property, userContext));
        json.put(Constants.PROP_TYPE,
            new JSONObject()
            .put(Constants.PROP_TYPE_NAME, type.getName())
            .put(Constants.PROP_TYPE_STATES, knownStates)
            .put(Constants.TYPE_IS_VM, Viewable.class.isAssignableFrom(type))
        );

        return json;
    }


    private static Object toJsonCompatibleValueFromProperty( Val<?> prop, UserContext userContext ) {

        if ( prop.isEmpty() ) return null;

        if ( prop.type() == Boolean.class )
            return prop.get();
        else if ( prop.type() == Integer.class )
            return prop.get();
        else if ( prop.type() == Double.class )
            return prop.get();
        else if ( prop.type() == Enum.class )
            return ((Enum)prop.get()).name();
        else if (Viewable.class.isAssignableFrom(prop.type())) {
            Viewable viewable = (Viewable) prop.get();
            if ( !userContext.hasVM(viewable) ) userContext.put(viewable);

            // We do not send the entire viewable object, but only the id
            return userContext.vmIdOf(viewable).toString();
        }
        else if ( prop.type() == Color.class ) {
            // In the frontend colors are usually hex strings
            Color color = (Color) prop.get();
            return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        }

        Object value = prop.get();
        String asString = String.valueOf(value);
        asString = asString.replace("\"", "\\\"");
        asString = asString.replace("\r", "\\r");
        asString = asString.replace("\n", "\\n");
        return asString;
    }

}
