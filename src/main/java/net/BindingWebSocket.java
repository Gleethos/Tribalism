package net;

import binding.UserContext;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import swingtree.api.mvvm.Action;
import swingtree.api.mvvm.ValDelegate;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

@WebSocket
public class BindingWebSocket {

    private final static Logger log = LoggerFactory.getLogger(BindingWebSocket.class);

    private final UserContext userContext;

    private Session session;

    public BindingWebSocket(UserContext userContext) {
        this.userContext = userContext;
    }

    private void _send( JSONObject json ) {
        try {
            String message = json.toString();
            session.getRemote().sendStringByFuture(message);
            log.debug("Sent: " + message);
        } catch (Throwable t) {
            log.error("Error sending message to websocket!", t);
        }
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        log.info("Connected to client: {}", session.getRemoteAddress().getAddress());
    }

    @OnWebSocketMessage
    public void onMessage(String message) {
        log.debug("Received: " + message);

        var json = new JSONObject(message);

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

    private void sendError(Exception e) {
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
        _send(returnJson);
    }

    private void sendVMToFrontend(JSONObject json) {
        String vmId = json.getString(Constants.VM_ID);
        JSONObject vmJson = new JSONObject();
        var vm = userContext.get(vmId);
        vmJson.put(Constants.EVENT_TYPE, Constants.RETURN_GET_VM);
        vmJson.put(Constants.EVENT_PAYLOAD, BindingUtil.toJson(vm, userContext));
        BindingUtil.bind( vm, new Action<ValDelegate<Object>>() {
            @Override
            public void accept(ValDelegate<Object> delegate) {
                try {
                    JSONObject update = new JSONObject();
                    update.put(Constants.EVENT_TYPE, Constants.RETURN_PROP);
                    update.put(Constants.EVENT_PAYLOAD,
                            BindingUtil.jsonFromProperty(delegate.getCurrent(), userContext)
                                    .put(Constants.VM_ID, vmId)
                    );
                    _send(update);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            @Override public boolean canBeRemoved() { return !session.isOpen(); }
        });
        // Send a message to the client that sent the message
        _send(vmJson);
    }

    private void applyMutationToVM(JSONObject json) {
        String vmId     = json.getString(Constants.VM_ID);
        String propName = json.getString(Constants.PROP_NAME);
        String value    = String.valueOf(json.get(Constants.PROP_VALUE));
        var vm = userContext.get(vmId);
        BindingUtil.applyToViewModelPropertyById(vm, propName, value);
    }

    private void callMethodOnVM(JSONObject json)
            throws
            InvocationTargetException,
            NoSuchMethodException,
            IllegalAccessException,
            ClassNotFoundException
    {
        String vmId     = json.getString(Constants.VM_ID);
        var vm = userContext.get(vmId);
        var result = BindingUtil.callViewModelMethod(vm, json.getJSONObject(Constants.EVENT_PAYLOAD), userContext);
        JSONObject returnJson = new JSONObject();
        returnJson.put(Constants.EVENT_TYPE, Constants.CALL_RETURN);
        returnJson.put(Constants.EVENT_PAYLOAD, result.put(Constants.VM_ID, vmId));
        _send(returnJson);
    }

}
