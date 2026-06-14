package dal.impl;

import dal.api.Value;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  Statically resolves a "zoom lens" property selector into a {@link Selection.Zoom}
 *  by inspecting the bytecode of a {@link dal.api.Model} default method with the Java 25
 *  {@link java.lang.classfile ClassFile API}.
 *  <p>
 *  The supported shape is intentionally strict: a default method on a {@code ModelTable}-backed
 *  model whose body does nothing but delegate to a {@code Value}-typed field getter and then
 *  chain one or more {@code zoomTo(getter, wither)} calls, e.g.
 *  <pre>{@code
 *    default Var<String> name() {
 *        return state().zoomTo(Product::name, Product::withName);
 *    }
 *    default Var<String> userAddressPostalCode() {
 *        return user().zoomTo(User::address,    User::withAddress)
 *                     .zoomTo(Address::postalCode, Address::withPostalCode);
 *    }
 *  }</pre>
 *  Each {@code zoomTo} getter (e.g. {@code Product::name}) is read out of the {@code invokedynamic}
 *  bootstrap constants and resolved against the value table of the current value type. Intermediate
 *  hops must be {@code Value} foreign keys; the final hop must be a primitive column.
 *  <p>
 *  Anything that does not fit this exact shape throws an {@link IllegalArgumentException} that
 *  explains precisely why the method could not be treated as a zoom selector.
 */
@NullMarked
final class ZoomLensResolver {

    private static final ConcurrentHashMap<Method, Selection.Zoom> CACHE = new ConcurrentHashMap<>();

    private ZoomLensResolver() {}

    static Selection.Zoom resolve(Method method, ModelTable modelTable, EntityRegistry registry) {
        return CACHE.computeIfAbsent(method, m -> _resolve(m, modelTable, registry));
    }

    private static Selection.Zoom _resolve(Method method, ModelTable modelTable, EntityRegistry registry) {
        CodeModel code = _codeOf(method);

        @Nullable EntityTableField rootField = null;
        @Nullable Class<?> currentValueType = null;
        @Nullable ValueTable currentVT = null;
        List<String> tables = new ArrayList<>();
        List<String> links = new ArrayList<>();
        @Nullable EntityTableField pendingSub = null; // the field a zoomTo resolved to, awaiting "is it the leaf?"
        List<LambdaImpl> pendingLambdas = new ArrayList<>();
        boolean rootSeen = false;

        for ( CodeElement element : code ) {
            if ( element instanceof InvokeDynamicInstruction idc ) {
                pendingLambdas.add(_lambdaImpl(idc, method));
            }
            else if ( element instanceof InvokeInstruction inv ) {
                String calledName = inv.name().stringValue();
                ClassDesc owner = inv.owner().asSymbol();

                if ( !rootSeen ) {
                    // The first call must be the Value-typed field getter on 'this'.
                    rootField = _findField(modelTable, calledName);
                    if ( rootField == null )
                        throw _fail(method, "it begins by calling '" + calledName + "()', which is not a persisted " +
                                "property of model table '" + modelTable.getTableName() + "'");
                    if ( !rootField.isForeignKey() || !Value.class.isAssignableFrom(rootField.type().item()) )
                        throw _fail(method, "the property '" + calledName + "()' it delegates to is not a Value-typed " +
                                "field; only fields holding a Value can be zoomed into");
                    currentValueType = rootField.type().item();
                    currentVT = _valueTable(registry, currentValueType, method);
                    tables.add(currentVT.getTableName());
                    rootSeen = true;
                }
                else {
                    // Every following call must be a zoomTo on a sprouts Val/Var.
                    if ( !calledName.equals("zoomTo") || !_isSproutsProperty(owner) )
                        throw _fail(method, "it calls '" + _simpleOf(owner) + "." + calledName + "(...)'; a zoom selector " +
                                "may only chain 'zoomTo(getter, wither)' calls after the initial field getter");
                    // rootSeen implies both are set; guard anyway to keep the null-checker happy.
                    if ( currentVT == null || currentValueType == null )
                        throw _fail(method, "its zoom chain could not be tracked from the initial field getter");

                    // A previous zoom step that is now being zoomed further must itself be a Value FK.
                    if ( pendingSub != null ) {
                        if ( !pendingSub.isForeignKey() || !Value.class.isAssignableFrom(pendingSub.type().item()) )
                            throw _fail(method, "the intermediate zoom step '" + pendingSub.baseName() + "' is not a " +
                                    "Value-typed field, so it cannot be zoomed into further");
                        links.add(pendingSub.name());
                        currentValueType = pendingSub.type().item();
                        currentVT = _valueTable(registry, currentValueType, method);
                        tables.add(currentVT.getTableName());
                    }

                    LambdaImpl getter = _pickGetter(pendingLambdas);
                    if ( getter == null )
                        throw _fail(method, "a 'zoomTo(..)' call has no recognizable field-getter method reference " +
                                "(expected something like " + _simpleName(currentValueType) + "::someField)");
                    if ( !getter.ownerDescriptor.equals(currentValueType.descriptorString()) )
                        throw _fail(method, "the zoom getter '" + getter.name + "' operates on '" + getter.ownerDescriptor +
                                "' but the value being zoomed is '" + currentValueType.getName() + "'");
                    EntityTableField sub = _findField(currentVT, getter.name);
                    if ( sub == null )
                        throw _fail(method, "value type '" + currentValueType.getName() + "' has no field named '" +
                                getter.name + "' to zoom into");
                    pendingSub = sub;
                    pendingLambdas.clear();
                }
            }
            // All other instructions (aload, checkcast, areturn, ...) are irrelevant to the shape.
        }

        if ( !rootSeen || rootField == null || pendingSub == null )
            throw _fail(method, "it never zooms into a value field (expected 'aValueField().zoomTo(Type::subField, ..)')");
        if ( !BasicSQLiteDataBase._isBasicDataType(pendingSub.type().item()) )
            throw _fail(method, "the final zoom target '" + pendingSub.baseName() + "' is not a primitive/queryable column " +
                    "(its type is '" + pendingSub.type().item().getName() + "'); only leaf primitives can be queried");

        return new Selection.Zoom(rootField.name(), List.copyOf(tables), List.copyOf(links), pendingSub.name());
    }

    /** A method reference captured from an {@code invokedynamic} (LambdaMetafactory) site. */
    private record LambdaImpl(String ownerDescriptor, String name, int parameterCount) {}

    /** Picks the field getter from the lambdas of a single {@code zoomTo} call: it takes the receiver only. */
    private static @Nullable LambdaImpl _pickGetter(List<LambdaImpl> lambdas) {
        for ( LambdaImpl l : lambdas )
            if ( l.parameterCount == 1 ) // getter as Function<T,B>: just the receiver, no extra args
                return l;
        return null;
    }

    private static LambdaImpl _lambdaImpl(InvokeDynamicInstruction idc, Method method) {
        var args = idc.invokedynamic().bootstrap().arguments();
        // LambdaMetafactory bootstrap args: [samMethodType, implMethod, instantiatedMethodType, ...]
        if ( args.size() < 2 || !(args.get(1) instanceof MethodHandleEntry mhe) )
            throw _fail(method, "a 'zoomTo(..)' argument is not a plain method reference");
        MethodHandleDesc mhd = mhe.asSymbol();
        if ( !(mhd instanceof DirectMethodHandleDesc dmh) )
            throw _fail(method, "a 'zoomTo(..)' argument is not a direct method reference");
        return new LambdaImpl(dmh.owner().descriptorString(), dmh.methodName(), dmh.invocationType().parameterCount());
    }

    private static @Nullable EntityTableField _findField(EntityTable table, String baseName) {
        for ( EntityTableField field : table.getFields() )
            if ( field.baseName().equals(baseName) )
                return field;
        return null;
    }

    private static ValueTable _valueTable(EntityRegistry registry, Class<?> valueType, Method method) {
        @SuppressWarnings("unchecked")
        var vt = registry.getValueTable((Class<? extends Value>) valueType).orElse(null);
        if ( vt == null )
            throw _fail(method, "no value table is registered for '" + valueType.getName() +
                    "' (pass it to createTablesFor(..))");
        return vt;
    }

    private static boolean _isSproutsProperty(ClassDesc owner) {
        String d = owner.descriptorString();
        return d.equals("Lsprouts/Var;") || d.equals("Lsprouts/Val;");
    }

    private static CodeModel _codeOf(Method method) {
        Class<?> declaring = method.getDeclaringClass();
        ClassModel cm = _parse(declaring, method);
        String wantName = method.getName();
        String wantDesc = _descriptorOf(method);
        MethodModel mm = cm.methods().stream()
                .filter(x -> x.methodName().stringValue().equals(wantName)
                          && x.methodType().stringValue().equals(wantDesc))
                .findFirst()
                .orElseThrow(() -> _fail(method, "its bytecode could not be located in " + declaring.getName()));
        return mm.code().orElseThrow(() -> _fail(method, "it has no method body to inspect"));
    }

    private static ClassModel _parse(Class<?> declaring, Method method) {
        String resource = declaring.getName().replace('.', '/') + ".class";
        ClassLoader cl = declaring.getClassLoader();
        if ( cl == null )
            cl = ClassLoader.getSystemClassLoader();
        try ( InputStream in = cl.getResourceAsStream(resource) ) {
            if ( in == null )
                throw _fail(method, "its class file '" + resource + "' could not be found on the classpath");
            return ClassFile.of().parse(in.readAllBytes());
        } catch ( IOException e ) {
            throw _fail(method, "its class file could not be read: " + e.getMessage());
        }
    }

    private static String _descriptorOf(Method method) {
        StringBuilder sb = new StringBuilder("(");
        for ( Class<?> p : method.getParameterTypes() )
            sb.append(p.descriptorString());
        return sb.append(')').append(method.getReturnType().descriptorString()).toString();
    }

    private static String _simpleOf(ClassDesc cd) {
        String d = cd.descriptorString();
        int slash = d.lastIndexOf('/');
        return slash >= 0 ? d.substring(slash + 1, d.length() - 1) : d;
    }

    private static String _simpleName(@Nullable Class<?> c) {
        return c == null ? "?" : c.getSimpleName();
    }

    private static IllegalArgumentException _fail(Method method, String reason) {
        return new IllegalArgumentException(
                "Cannot use '" + method.getDeclaringClass().getSimpleName() + "::" + method.getName() +
                "' as a query property selector, because " + reason + ".\n" +
                "A zoom selector must be a default method that does nothing but delegate to a Value-typed " +
                "field and chain 'zoomTo(getter, wither)' calls down to a primitive value field."
        );
    }
}
