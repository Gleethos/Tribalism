/*
    Some constants needed to communicate with the server:
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */

export class Constants {
    static readonly EVENT_TYPE = 'EventType';
    static readonly EVENT_PAYLOAD = 'EventPayload';
    // Event Type Values:
    static readonly SET_PROP = 'act';
    static readonly RETURN_PROP = 'show';
    static readonly GET_VM = 'getVM';
    static readonly RETURN_GET_VM = 'viewModel';
    static readonly CALL = 'call';
    static readonly CALL_RETURN = 'callReturn';
    static readonly ERROR = 'error';
    // View model properties:
    static readonly VM_ID = 'vmId';
    static readonly CLASS_NAME = 'className';
    static readonly PROPS = 'props';
    static readonly METHOD_NAME = 'name';
    static readonly METHOD_ARG_NAME = 'name';
    static readonly METHOD_ARG_TYPE = 'type';
    static readonly TYPE_NAME = 'type';
    static readonly TYPE_IS_VM = 'viewable';
    static readonly METHOD_ARG_TYPE_IS_VM = 'viewable';
    static readonly METHOD_ARGS = 'args';
    static readonly METHOD_RETURNS = 'returns';
    // Property properties:
    static readonly PROP_NAME = 'propName';
    static readonly PROP_VALUE = 'value';
    static readonly PROP_TYPE = 'type';
    static readonly PROP_TYPE_NAME = 'name';
    static readonly PROP_TYPE_STATES = 'states';
    // Error properties:
    static readonly ERROR_MESSAGE = 'message';
    static readonly ERROR_STACK_TRACE = 'stackTrace';
    static readonly ERROR_TYPE = 'type';
}