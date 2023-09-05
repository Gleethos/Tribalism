package net;

import app.ViewModel;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sprouts.Action;
import sprouts.Val;
import swingtree.threading.EventProcessor;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 *  This is where the messages received from the {@link Connection} are interpreted
 *  and handled.
 *  This class is responsible for sending messages to the client through the {@link SocketSession}.
 */
public class WebUserSession
{
    private final static Logger log = LoggerFactory.getLogger(WebUserSession.class);

    private final WebUserContext webUserContext;
    private final SocketSession socket;


    public WebUserSession(WebUserContext webUserContext, SocketSession socketSession) {
        this.webUserContext = webUserContext;
        this.socket = socketSession;
    }

    /**
     *  This is where all the messages are received from the web-frontend client!
     *
     * @param json The message received from the React client.
     */
    public void receive(JSONObject json) {
        if ( !json.has(Constants.EVENT_TYPE) ) return;

        String type = json.getString(Constants.EVENT_TYPE);

        if ( type.equals(Constants.GET_VM) ) {
            try {
                sendVMToFrontend(json);
            } catch (Exception e) {
                log.error("Error sending VM to frontend", e);
                e.printStackTrace();
                sendError(e);
            }
        }
        else if ( type.equals(Constants.SET_PROP) ) {
            try {
                applyMutationToVM(json);
            } catch (Exception e) {
                log.error("Error applying mutation to VM", e);
                e.printStackTrace();
                sendError(e);
            }
        }
        else if ( type.equals(Constants.CALL) ) {
            try {
                callMethodOnVM(json);
            } catch (Exception e) {
                log.error("Error calling method on VM", e);
                e.printStackTrace();
                sendError(e);
            }
        }
        else if ( type.equals(Constants.ERROR) ) {
            log.error("Error from frontend: " + json.getString(Constants.EVENT_PAYLOAD));
        }
        else {
            log.error("Unknown event type: " + type);
        }

    }

    void sendError(Exception e) {
        var returnJson = new JSONObject();
        var errorJson = new JSONObject();
        returnJson.put(Constants.EVENT_TYPE, Constants.ERROR);
        errorJson.put(Constants.ERROR_MESSAGE, e.getMessage());
        List<String> stackTrace = new ArrayList<>();
        for ( StackTraceElement element : e.getStackTrace() )
            stackTrace.add(element.toString());

        errorJson.put(Constants.ERROR_STACK_TRACE, stackTrace);
        errorJson.put(Constants.ERROR_TYPE, e.getClass().getName());
        returnJson.put(Constants.EVENT_PAYLOAD, errorJson);
        socket.send(returnJson);
    }

    private void sendVMToFrontend(JSONObject json) {
        if ( !json.has(Constants.VM_ID) ) {
            throw new RuntimeException("No view model ID in message: '" + json + "', need VMID to send VM to frontend!");
        }
        String vmId = json.getString(Constants.VM_ID);
        JSONObject vmJson = new JSONObject();
        var vm = webUserContext.get(vmId);
        if ( vm == null )
            throw new RuntimeException("No view model with ID '" + vmId + "' found in web user session with ID '" + socket.creationTime() + "'!");

        vmJson.put(Constants.EVENT_TYPE, Constants.RETURN_GET_VM);
        vmJson.put(Constants.EVENT_PAYLOAD, toJson(vm));
        bindTo(vm, vmId);
        // Send a message to the client that sent the message
        socket.send(vmJson);
    }

    private void bindTo(Object vm, String vmId) {
        long httpSessionCreationTime = socket.creationTime();
        ReflectionUtil.bind( vm, new Action<>() {
            @Override
            public void accept(Val<Object> val) {
                try {
                    JSONObject update = new JSONObject();
                    update.put(Constants.EVENT_TYPE, Constants.RETURN_PROP);
                    update.put(Constants.EVENT_PAYLOAD,
                            jsonFromProperty(val)
                                    .put(Constants.VM_ID, vmId)
                    );
                    socket.send(update);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            @Override public boolean canBeRemoved() {
                boolean observerInvalid = httpSessionCreationTime != socket.creationTime();
                if ( observerInvalid )
                    log.info("Observer is invalid, removing it!");
                return observerInvalid;
            }
        });
    }

    private void applyMutationToVM(JSONObject json) {
        String vmId     = json.getString(Constants.VM_ID);
        String propName = json.getString(Constants.PROP_NAME);
        String value    = String.valueOf(json.get(Constants.PROP_VALUE));
        var vm = webUserContext.get(vmId);
        ReflectionUtil.applyToViewModelPropertyById(vm, propName, value);
    }

    private void callMethodOnVM(JSONObject json)
            throws
            InvocationTargetException,
            NoSuchMethodException,
            IllegalAccessException,
            ClassNotFoundException
    {
        String vmId     = json.getString(Constants.VM_ID);
        var vm = webUserContext.get(vmId);
        var result = callViewModelMethod(vm, json.getJSONObject(Constants.EVENT_PAYLOAD));
        JSONObject returnJson = new JSONObject();
        returnJson.put(Constants.EVENT_TYPE, Constants.CALL_RETURN);
        returnJson.put(Constants.EVENT_PAYLOAD, result.put(Constants.VM_ID, vmId));
        socket.send(returnJson);
    }


    public JSONObject callViewModelMethod(
            Object vm,
            JSONObject methodCallData
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
        Object result = null;
        Supplier<Object> invoker;
        if ( methodArgs.length == 0 ) {
            method = vm.getClass().getMethod(methodName);
            invoker = () -> {
                try {
                    return method.invoke(vm);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
                return null;
            };
        } else {
            method = vm.getClass().getMethod(methodName, methodArgs[0].getClass());
            invoker = () -> {
                try {
                    return method.invoke(vm, methodArgs[0]);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
                return null;
            };
        }

        // Now we need to check if the method is returning void or not!
        // This is important with respect to the threading
        // -> We want this to be executed on the application thread, but how?
        boolean returnsNothing = method.getReturnType().equals(Void.TYPE);
        if ( returnsNothing )
            EventProcessor.DECOUPLED.registerAppEvent(invoker::get); // Just send it to the app thread
        else {
            Object[] resultHolder = new Object[1];
            EventProcessor.DECOUPLED.registerAndRunAppEventNow(() -> resultHolder[0] = invoker.get()); // We need to wait for the result!
            result = resultHolder[0];
        }

        if ( result instanceof Val<?> property )
            result = jsonFromProperty(property);

        return new JSONObject()
                .put(Constants.METHOD_NAME, methodName)
                .put(Constants.METHOD_RETURNS, result);
    }


    public JSONObject toJson(Object vm) {
        Objects.requireNonNull(vm);
        JSONObject json = new JSONObject();
        for ( var property : ReflectionUtil.findPropertiesInViewModel(vm) )
            json.put(property.id(), jsonFromProperty(property));

        JSONObject result = new JSONObject();
        result.put(Constants.PROPS, json);
        result.put(Constants.CLASS_NAME, vm.getClass().getName());
        result.put(Constants.VM_ID, webUserContext.vmIdOf(vm).toString());
        result.put("methods", ReflectionUtil.getMethodsForViewModel(vm));
        return result;
    }

    public JSONObject jsonFromProperty(
            Val<?> property
    ) {
        Class<?> type = property.type();
        List<String> knownStates = new ArrayList<>();
        if ( Enum.class.isAssignableFrom(type) ) {
            for ( var state : type.getEnumConstants() )
                knownStates.add(((Enum)state).name());
        }
        JSONObject json = new JSONObject();
        json.put(Constants.PROP_NAME, property.id());
        json.put(Constants.PROP_VALUE, toJsonCompatibleValueFromProperty(property));
        json.put(Constants.PROP_TYPE,
                new JSONObject()
                        .put(Constants.PROP_TYPE_NAME, type.getName())
                        .put(Constants.PROP_TYPE_STATES, knownStates)
                        .put(Constants.TYPE_IS_VM, ViewModel.class.isAssignableFrom(type))
        );

        return json;
    }


    Object toJsonCompatibleValueFromProperty(Val<?> prop) {

        if ( prop.isEmpty() ) // We return a json null if the property is empty
            return JSONObject.NULL;


        if ( prop.type() == Boolean.class )
            return prop.get();
        else if ( prop.type() == Integer.class )
            return prop.get();
        else if ( prop.type() == Double.class )
            return prop.get();
        else if ( prop.type() == Enum.class )
            return ((Enum)prop.get()).name();
        else if (ViewModel.class.isAssignableFrom(prop.type())) {
            ViewModel viewModel = (ViewModel) prop.get();
            if ( !webUserContext.hasVM(viewModel) ) {
                webUserContext.put(viewModel);
                bindTo(viewModel, webUserContext.vmIdOf(viewModel).toString());
            }

            // We do not send the entire viewable object, but only the id
            return webUserContext.vmIdOf(viewModel).toString();
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
