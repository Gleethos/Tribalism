package dal.impl;

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
 *  The supported shape is intentionally strict: a default method whose body does nothing but
 *  delegate to a {@code Value}-typed field getter and then chain one or more
 *  {@code zoomTo(getter, wither)} calls, e.g.
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
 *  bootstrap constants; the ordered chain of getter names is then handed to {@link ZoomPaths} for
 *  validation against the value tables. Anything that does not fit this exact shape throws an
 *  {@link IllegalArgumentException} explaining precisely why.
 *  <p>
 *  This handles the case where the zoom lens lives as a default method <em>on the model interface</em>.
 *  For zoom navigation passed inline as lambdas/method-references to {@code where(..)}, see
 *  {@link NestedSelectionResolver}.
 */
@NullMarked
final class ZoomLensResolver {

    private static final ConcurrentHashMap<Method, Selection.Zoom> CACHE = new ConcurrentHashMap<>();

    private ZoomLensResolver() {}

    static Selection.Zoom resolve(Method method, ModelTable modelTable, EntityRegistry registry) {
        return CACHE.computeIfAbsent(method, m -> _resolve(m, modelTable, registry));
    }

    private static Selection.Zoom _resolve(Method method, ModelTable modelTable, EntityRegistry registry) {
        String what = method.getDeclaringClass().getSimpleName() + "::" + method.getName();
        CodeModel code = _codeOf(method, what);

        @Nullable EntityTableField rootField = null;
        List<String> getterNames = new ArrayList<>();
        List<LambdaImpl> pendingLambdas = new ArrayList<>();
        boolean rootSeen = false;

        for ( CodeElement element : code ) {
            if ( element instanceof InvokeDynamicInstruction idc ) {
                pendingLambdas.add(_lambdaImpl(idc, what));
            }
            else if ( element instanceof InvokeInstruction inv ) {
                String calledName = inv.name().stringValue();
                ClassDesc owner = inv.owner().asSymbol();

                if ( !rootSeen ) {
                    // The first call must be the field getter on 'this'.
                    rootField = ZoomPaths.findField(modelTable, calledName);
                    if ( rootField == null )
                        throw ZoomPaths.fail(what, "it begins by calling '" + calledName + "()', which is not a " +
                                "persisted property of model table '" + modelTable.getTableName() + "'");
                    rootSeen = true;
                }
                else {
                    // Every following call must be a zoomTo on a sprouts Val/Var.
                    if ( !calledName.equals("zoomTo") || !_isSproutsProperty(owner) )
                        throw ZoomPaths.fail(what, "it calls '" + _simpleOf(owner) + "." + calledName + "(...)'; a zoom " +
                                "selector may only chain 'zoomTo(getter, wither)' calls after the initial field getter");
                    LambdaImpl getter = _pickGetter(pendingLambdas);
                    if ( getter == null )
                        throw ZoomPaths.fail(what, "a 'zoomTo(..)' call has no recognizable field-getter method reference");
                    getterNames.add(getter.name);
                    pendingLambdas.clear();
                }
            }
            // All other instructions (aload, checkcast, areturn, ...) are irrelevant to the shape.
        }

        if ( !rootSeen || rootField == null || getterNames.isEmpty() )
            throw ZoomPaths.fail(what, "it never zooms into a value field (expected 'aValueField().zoomTo(Type::subField, ..)')");

        return ZoomPaths.build(rootField, getterNames, registry, what);
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

    private static LambdaImpl _lambdaImpl(InvokeDynamicInstruction idc, String what) {
        var args = idc.invokedynamic().bootstrap().arguments();
        // LambdaMetafactory bootstrap args: [samMethodType, implMethod, instantiatedMethodType, ...]
        if ( args.size() < 2 || !(args.get(1) instanceof MethodHandleEntry mhe) )
            throw ZoomPaths.fail(what, "a 'zoomTo(..)' argument is not a plain method reference");
        MethodHandleDesc mhd = mhe.asSymbol();
        if ( !(mhd instanceof DirectMethodHandleDesc dmh) )
            throw ZoomPaths.fail(what, "a 'zoomTo(..)' argument is not a direct method reference");
        return new LambdaImpl(dmh.owner().descriptorString(), dmh.methodName(), dmh.invocationType().parameterCount());
    }

    private static boolean _isSproutsProperty(ClassDesc owner) {
        String d = owner.descriptorString();
        return d.equals("Lsprouts/Var;") || d.equals("Lsprouts/Val;");
    }

    private static CodeModel _codeOf(Method method, String what) {
        Class<?> declaring = method.getDeclaringClass();
        ClassModel cm = _parse(declaring, what);
        String wantName = method.getName();
        String wantDesc = _descriptorOf(method);
        MethodModel mm = cm.methods().stream()
                .filter(x -> x.methodName().stringValue().equals(wantName)
                          && x.methodType().stringValue().equals(wantDesc))
                .findFirst()
                .orElseThrow(() -> ZoomPaths.fail(what, "its bytecode could not be located in " + declaring.getName()));
        return mm.code().orElseThrow(() -> ZoomPaths.fail(what, "it has no method body to inspect"));
    }

    private static ClassModel _parse(Class<?> declaring, String what) {
        String resource = declaring.getName().replace('.', '/') + ".class";
        ClassLoader cl = declaring.getClassLoader();
        if ( cl == null )
            cl = ClassLoader.getSystemClassLoader();
        try ( InputStream in = cl.getResourceAsStream(resource) ) {
            if ( in == null )
                throw ZoomPaths.fail(what, "its class file '" + resource + "' could not be found on the classpath");
            return ClassFile.of().parse(in.readAllBytes());
        } catch ( IOException e ) {
            throw ZoomPaths.fail(what, "its class file could not be read: " + e.getMessage());
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
}
