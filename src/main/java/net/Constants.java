package net;

public class Constants {

    public static String EVENT_TYPE = "EventType";
    public static String EVENT_PAYLOAD = "EventPayload";

    // Event Type Values:
    public static String SET_PROP = "act";
    public static String RETURN_PROP = "show";
    public static String GET_VM = "getVM";
    public static String RETURN_GET_VM = "viewModel";
    public static String CALL          = "call";
    public static String CALL_RETURN   = "callReturn";
    public static String ERROR         = "error";


    // View model properties:
    public static String VM_ID = "vmId";
    public static String PROPS = "props";
    public static String METHOD_NAME = "name";
    public static String METHOD_ARG_NAME = "name";
    public static String METHOD_ARG_TYPE = "type";
    public static String TYPE_NAME = "type";
    public static String TYPE_IS_VM = "viewable";
    public static String METHOD_ARGS = "args";
    public static String METHOD_RETURNS = "returns";


    // Property properties:

    public static String PROP_NAME = "propName";
    public static String PROP_VALUE = "value";
    public static String PROP_TYPE = "type";
    public static String PROP_TYPE_NAME = "name";
    public static String PROP_TYPE_STATES = "states";

    // Error properties:
    public static String ERROR_MESSAGE = "message";
    public static String ERROR_STACK_TRACE = "stackTrace";
    public static String ERROR_TYPE = "type";
}
