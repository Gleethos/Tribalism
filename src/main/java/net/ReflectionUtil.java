package net;

import app.Viewable;
import org.json.JSONArray;
import org.json.JSONObject;
import sprouts.Action;
import sprouts.Val;
import sprouts.Var;

import java.util.List;

public class ReflectionUtil {

    private ReflectionUtil() {}


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
                if ( value instanceof sprouts.Val<?> val )
                    properties.add((Val<Object>) val);

            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return properties;
    }


    public static <T> Var<T> findViewModelPropById( Object vm, String id ) {
        return ReflectionUtil.findPropertiesInViewModel(vm)
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
        Var<Object> prop = ReflectionUtil.findViewModelPropById(vm, id);

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

    public static void bind(
            Object vm,
            Action<Val<Object>> observer
    ) {
        ReflectionUtil.findPropertiesInViewModel(vm).forEach(p -> p.onSet(observer) );
    }

    static JSONArray getMethodsForViewModel(Object vm) {
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

}
