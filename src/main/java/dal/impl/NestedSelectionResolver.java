package dal.impl;

import dal.api.Value;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import sprouts.Tuple;

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 *  Resolves a chain of inline value-navigation functions (e.g. {@code User::address},
 *  {@code Address::postalCode}, or {@code u -> u.address().postalCode()}) into a
 *  {@link Selection.Zoom}, without inspecting bytecode.
 *  <p>
 *  Bytecode inspection is not viable here: in Groovy (and generally for arbitrary lambdas) the
 *  functions arrive as opaque proxies, not as inspectable {@code LambdaMetafactory} targets. Instead
 *  this resolver builds a <em>probe</em> of the root value type — a fully-populated sentinel value
 *  tree in which every field holds a unique marker — and then simply <em>executes</em> the navigation
 *  functions against it. The returned marker is matched back to the exact field path it came from.
 *  <p>
 *  Because the navigation is just run, it is robust to however the lambda was compiled. Misuse is
 *  caught structurally: if a function does anything other than a pure chain of value-field accessors
 *  (computes a value, returns a constant, throws, dives into a tuple, ...), the returned object is not
 *  a known marker and a descriptive {@link IllegalArgumentException} is thrown.
 */
@NullMarked
final class NestedSelectionResolver {

    private NestedSelectionResolver() {}

    static Selection.Zoom resolve(
        EntityTableField rootField,
        List<Function<Object, Object>> lenses,
        EntityRegistry registry,
        String what
    ) {
        if ( !rootField.isForeignKey() || !Value.class.isAssignableFrom(rootField.type().item()) )
            throw ZoomPaths.fail(what, "the root property '" + rootField.baseName() + "' is not a Value-typed field");
        if ( lenses.isEmpty() )
            throw ZoomPaths.fail(what, "no nested value field was selected");

        // 1. Build a probe value tree of the root value type, recording every node's field path.
        Probe probe = new Probe(registry, what);
        Object root = probe.build(rootField.type().item(), List.of(), new HashSet<>());

        // 2. Execute the navigation functions, threading the result through the chain.
        Object current = root;
        for ( Function<Object, Object> lens : lenses ) {
            try {
                current = lens.apply(current);
            } catch ( RuntimeException | Error e ) {
                throw ZoomPaths.fail(what, "evaluating a navigation function failed (" + e.getClass().getSimpleName() +
                        "); a nested selector must be a pure chain of value-field accessors");
            }
            if ( current == null )
                throw ZoomPaths.fail(what, "a navigation function returned null; it must be a pure chain of value-field accessors");
        }

        // 3. Map the resulting marker back to the field path it identifies.
        List<String> path = probe.pathOf(current);
        if ( path == null )
            throw ZoomPaths.fail(what, "the navigation did not resolve to a known nested value field; it must be a pure " +
                    "chain of value-field accessors like 'v -> v.a().b()'");
        if ( probe.isAmbiguous(current) )
            throw ZoomPaths.fail(what, "the navigated field could not be uniquely identified (its value type has " +
                    "indistinguishable sentinel values); use the explicit where(root, Type::field, ..) form instead");

        return ZoomPaths.build(rootField, path, registry, what);
    }

    /**
     *  Builds a probe value tree and remembers, for every produced marker/sub-value, the path of
     *  field names that reaches it from the root value.
     */
    private static final class Probe {
        private final EntityRegistry registry;
        private final String what;
        private final Map<Object, List<String>> pathByMarker = new HashMap<>();
        private final Set<Object> ambiguous = new HashSet<>();
        private int counter = 0;

        Probe(EntityRegistry registry, String what) {
            this.registry = registry;
            this.what = what;
        }

        @Nullable List<String> pathOf(Object marker) { return pathByMarker.get(marker); }
        boolean isAmbiguous(Object marker) { return ambiguous.contains(marker); }

        Object build(Class<?> valueType, List<String> prefix, Set<Class<?>> onStack) {
            if ( !onStack.add(valueType) )
                throw ZoomPaths.fail(what, "value type '" + valueType.getName() + "' is recursively self-referential");
            // Validate it is a known value table (gives a good error early):
            ZoomPaths.valueTable(registry, valueType, what);
            RecordComponent[] components = valueType.getRecordComponents();
            if ( components == null )
                throw ZoomPaths.fail(what, "value type '" + valueType.getName() + "' is not a record");

            Class<?>[] paramTypes = new Class<?>[components.length];
            Object[] args = new Object[components.length];
            for ( int i = 0; i < components.length; i++ ) {
                RecordComponent c = components[i];
                Class<?> ct = c.getType();
                paramTypes[i] = ct;
                List<String> compPath = _append(prefix, c.getName());
                if ( BasicSQLiteDataBase._isBasicDataType(ct) ) {
                    Object marker = _marker(ct, counter++);
                    _register(marker, compPath);
                    args[i] = marker;
                } else if ( Value.class.isAssignableFrom(ct) ) {
                    Object nested = build(ct, compPath, onStack);
                    _register(nested, compPath);
                    args[i] = nested;
                } else if ( Tuple.class.isAssignableFrom(ct) ) {
                    args[i] = _emptyTuple(c); // not navigable; intentionally not registered
                } else {
                    throw ZoomPaths.fail(what, "value type '" + valueType.getName() + "' has a component '" + c.getName() +
                            "' of unsupported type '" + ct.getName() + "' for nested selection");
                }
            }
            onStack.remove(valueType);
            try {
                Constructor<?> ctor = valueType.getDeclaredConstructor(paramTypes);
                ctor.setAccessible(true);
                return ctor.newInstance(args);
            } catch ( ReflectiveOperationException e ) {
                throw ZoomPaths.fail(what, "could not instantiate a probe of value type '" + valueType.getName() +
                        "' (" + e.getClass().getSimpleName() + ")");
            }
        }

        private void _register(Object marker, List<String> path) {
            List<String> prev = pathByMarker.putIfAbsent(marker, path);
            if ( prev != null && !prev.equals(path) )
                ambiguous.add(marker); // two distinct fields produced an indistinguishable marker
        }

        private static List<String> _append(List<String> prefix, String name) {
            List<String> out = new ArrayList<>(prefix.size() + 1);
            out.addAll(prefix);
            out.add(name);
            return List.copyOf(out);
        }

        private Tuple<?> _emptyTuple(RecordComponent c) {
            Type gt = c.getGenericType();
            Class<?> elem = Object.class;
            if ( gt instanceof ParameterizedType pt && pt.getActualTypeArguments()[0] instanceof Class<?> ec )
                elem = ec;
            return Tuple.of(elem);
        }

        /** A per-field unique sentinel value of the given primitive/leaf type. */
        private Object _marker(Class<?> type, int n) {
            if ( type == String.class )                              return "§zoom-sentinel-" + n + "§";
            if ( type == int.class || type == Integer.class )       return 1_000_000 + n;
            if ( type == long.class || type == Long.class )         return 1_000_000L + n;
            if ( type == double.class || type == Double.class )     return 1_000_000.0 + n;
            if ( type == float.class || type == Float.class )       return 1_000_000.0f + n;
            if ( type == short.class || type == Short.class )       return (short) (1_000 + n);
            if ( type == byte.class || type == Byte.class )         return (byte) n;
            if ( type == char.class || type == Character.class )    return (char) ('A' + n);
            if ( type == boolean.class || type == Boolean.class )   return (n % 2 == 0);
            if ( type == LocalDateTime.class )                      return LocalDateTime.of(2000, 1, 1, 0, 0).plusSeconds(n);
            if ( Enum.class.isAssignableFrom(type) ) {
                Object[] constants = type.getEnumConstants();
                return constants[n % constants.length];
            }
            throw ZoomPaths.fail(what, "cannot build a probe value for primitive type '" + type.getName() + "'");
        }
    }
}
