/*
    Some constants needed to communicate with the server:
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */

const EVENT_TYPE = "EventType";
const EVENT_PAYLOAD = "EventPayload";

// Event Type Values:
const SET_PROP      = "act";
const RETURN_PROP   = "show";
const GET_VM        = "getVM";
const RETURN_GET_VM = "viewModel";
const CALL          = "call";
const CALL_RETURN   = "callReturn";
const ERROR         = "error";

// View model properties:
const VM_ID = "vmId";
const PROPS = "props";
const METHOD_NAME = "name";
const METHOD_ARG_NAME = "name";
const METHOD_ARG_TYPE = "type";
const TYPE_NAME = "type";
const TYPE_IS_VM = "viewable";
const METHOD_ARG_TYPE_IS_VM = "viewable";
const METHOD_ARGS     = "args";
const METHOD_RETURNS = "returns";

// Property properties:
const PROP_NAME        = "propName";
const PROP_VALUE       = "value";
const PROP_TYPE        = "type";
const PROP_TYPE_NAME   = "name";
const PROP_TYPE_STATES = "states";

// Error properties:
const ERROR_MESSAGE = "message";
const ERROR_STACK_TRACE = "stackTrace";
const ERROR_TYPE = "type";

/*
    First we define the API for interacting with the MVVM backend:
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */

function Var(get, set, observe, type) {
    Var.prototype.getOnce = get;
    Var.prototype.onShow = observe;
    Var.prototype.type = type;
    Var.prototype.get = (consumer) => { get(consumer); observe(consumer); }
    Var.prototype.set = set;
}

function Val(get, observe, type) {
    Val.prototype.getOnce = get;
    Val.prototype.onShow = observe;
    Val.prototype.type = type;
    Val.prototype.get = (consumer) => { get(consumer); observe(consumer); }
}

function Get(get) {
    Get.prototype.get = get;
}

/**
 *  This is used as a representation of a view model.
 *  it exposes the current state of the view model as
 *  well as a way to bind to its properties in both ways.
 *
 * @param  session the session that this view model belongs to, this is used to load sub-view models
 * @param  vm the state of the view model (a json object)
 * @param  vmSet a function for setting a property of the view model
 * @param  vmObserve a function for registering property observers
 * @param  vmCall
 * @return vmGet a function for registering an observer for a property of the view model in the backend
 * @constructor
 */
function VM(
    session,   // For loading view models like this one
    vm,        // The current view model
    vmSet,     // Send a property change to the server, expects 2 arguments: propName, value
    vmObserve, // For binding to properties, expects 2 parameters: the property name and the action to call when the property changes
    vmCall     // For calling methods, expects 3 parameters: the method name, the arguments and the action to call when the method returns
){
    this.state = vm;
    // Now we mirror the methods of the Java view model in JS!
    const methods = vm.methods;
    for (let i = 0; i < methods.length; i++) {
        const method = methods[i];
        // Currently we only support void methods:
        if ( method[METHOD_RETURNS][TYPE_NAME] === "void" ) {
            this[method[METHOD_NAME]] = (...args) => {
                vmCall(
                    method[METHOD_NAME],
                    args,
                    () => {}
                );
            }
        } else if ( method[METHOD_RETURNS][TYPE_NAME] === "Var" || method[METHOD_RETURNS][TYPE_NAME] === "Val" ) {
            this[method[METHOD_NAME]] = (...args)=>{
                const propGet = (consumer) => {
                    vmCall(
                        method[METHOD_NAME],
                        args,
                        (property) => {
                            console.log("Got property: " + property);
                            // If the property value is a view model, we need to load it:
                            if ( property[PROP_TYPE][TYPE_IS_VM] ) {
                                session.get(
                                    property[PROP_VALUE],
                                    (vm) => consumer(vm) // Here we expect a VM object where the user can bind to...
                                );
                            }
                            else
                                consumer(property[PROP_VALUE]); // This is a primitive value, we can just pass it on...
                        }
                    );
                };

                const propObserve = (consumer) => {
                    vmCall(
                        method[METHOD_NAME],
                        args,
                        (property) => {
                            vmObserve(property[PROP_NAME], p => {
                                // If the property value is a view model, we need to load it:
                                if ( p[PROP_TYPE][TYPE_IS_VM] ) {
                                    session.get(
                                        p[PROP_VALUE],
                                        (vm) => {
                                            // Here we expect a VM object!
                                            consumer(vm);
                                            // The user can call methods on the VM object...
                                        }
                                    );
                                }
                                else
                                    consumer(p[PROP_VALUE]) // Here we expect a primitive value!
                            });
                        }
                    );
                };

                const propSet = (newValue) => {
                    vmCall(
                        method[METHOD_NAME],
                        args,
                        (property) => {
                            vmSet(
                                property[PROP_NAME],
                                newValue
                            );
                        }
                    );
                };
                const propType = (consumer) => {
                    vmCall(
                        method[METHOD_NAME],
                        args,
                        (property) => {
                            consumer(property[PROP_TYPE]);
                        }
                    );
                };

                if (method[METHOD_RETURNS][TYPE_NAME] === "Var") {
                    return new Var(propGet, propSet, propObserve, propType);
                } else {
                    return new Val(propGet, propObserve, propType);
                }
            }
        } else {
            this[method[METHOD_NAME]] = (...args)=>{
                return new Get((consumer) => {
                    vmCall(
                        method[METHOD_NAME],
                        args,
                        (property) => {
                            const value = property[PROP_VALUE];
                            consumer(value);
                        }
                    );
                });
            }
        }
    }
}

/**
 *  This is a representation of a websocket session.
 *  It allows you to fetch new view models from the server.
 *
 * @param getViewModel a function for fetching a view model from the server
 * @constructor
 */
function Session(
    getViewModel // For loading a view model, expects 2 parameters: the view model id and the action to call when the view model is loaded
) {
    Session.prototype.get = getViewModel;
}

/*
    The last part of the API for doing MVVM binding is the entry point
    to a web socket server connection.
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
*/

/**
 *  This is the entrypoint for the MVVM binding.
 *  Here you can connect to a websocket and get a view model
 *  through the second parameter, a function that will receive the above defined
 *  Session object as well as VM object.
 *
 * @param serverAddress the address of the web-socket server to connect to
 * @param iniViewModelId the id of the view model to load
 * @param frontend the function to call when the view model is loaded
 */
function start(serverAddress, iniViewModelId, frontend) {
    let ws = null;
    const propertyObservers = {};
    const viewModelObservers = {};
    const methodObservers = {};
    const session = new Session((vmId, action) => {
                            viewModelObservers[vmId] = action;
                            sendVMRequest(vmId);
                        });

    function startWebsocket(action) {
        ws = new WebSocket(serverAddress)
        ws.onopen = () => { action(); };
        ws.onclose = () => {
            // connection closed, discard old websocket and create a new one in 5s
            ws = null;
            setTimeout(() => startWebsocket( ()=>{} ), 5000);
        }
        ws.onmessage = (event) => {
            //console.log('Message from server: ' + event.data);
            // We parse the data as json:
            processResponse(JSON.parse(event.data));
        };
    }
    startWebsocket( () => sendVMRequest(iniViewModelId) );

    function send(data) {
        // First up: If the message is a JSON we turn it into a string:
        const message = typeof data === "string" ? data : JSON.stringify(data);

        if ( ws ) {
            // The web socket might be closed, if so we reopen it
            // and send the message when it is open again:
            if ( ws.readyState === WebSocket.CLOSED ) {
                startWebsocket(() => { send(message); });
                return;
            }
            ws.send(message);
        }
        else {
            console.log("Websocket missing! Failed to send message '" + message + "'. Retrying in 100ms.");
            // The web socket is not open yet, so we try again in 100ms:
            setTimeout(() => send(message), 100);
        }
    }

    function sendVMRequest(vmId) {
        console.log("Requesting view model: " + vmId);
        send({[EVENT_TYPE]: GET_VM, [VM_ID]: vmId});
    }

    function processResponse(data) {
        // Now let's check the EventType: either a view model or a property change...
        if ( data[EVENT_TYPE] === RETURN_GET_VM ) {
            // We have a view model, so we can set it as the current view model:
            const viewModel = data[EVENT_PAYLOAD];
            const vmId = viewModel[VM_ID];

            const vm = new VM(
                session,
                viewModel,
                (propName, value) => {
                    send({
                        [EVENT_TYPE]: SET_PROP,
                        [VM_ID]: vmId,
                        [PROP_NAME]: propName,
                        [PROP_VALUE]: value,
                    });
                },
                (propName, action) => {
                    propertyObservers[vmId + ":" + propName] = action;
                },
                (methodName, args, action) => {
                    let key = vmId + ":" + methodName;
                    if ( !methodObservers[key] ) methodObservers[key] = [];
                    methodObservers[key].push(action);
                    send({
                        [EVENT_TYPE]: CALL,
                        [EVENT_PAYLOAD]: {
                            [METHOD_NAME]: methodName,
                            [METHOD_ARGS]: args
                        },
                        [VM_ID]: vmId
                    });
                }
            );

            if ( viewModelObservers[vmId] ) {
                viewModelObservers[vmId](vm);
                return;
            }
            frontend(session,vm);
        } else if (data[EVENT_TYPE] === RETURN_PROP) {
            // We look up the binding for the property change:
            const action = propertyObservers[data[EVENT_PAYLOAD][VM_ID] + ":" + data[EVENT_PAYLOAD][PROP_NAME]];
            // If we have a binding, we call it with the new value:
            if (action)
                action(data[EVENT_PAYLOAD]);
            else
                console.log("No action for property observation event: " + JSON.stringify(data));
        } else if (data[EVENT_TYPE] === CALL_RETURN) {
            const actions = methodObservers[data[EVENT_PAYLOAD][VM_ID] + ":" + data[EVENT_PAYLOAD][METHOD_NAME]];
            if ( actions ) {
                // There should at least be one action, if not we log this as an error:
                if (actions.length === 0) {
                    console.log("No actions for method: " + data[EVENT_PAYLOAD][METHOD_NAME]);
                    return;
                }
                // We get and remove the first action from the list:
                let action = actions.shift();

                // The action should not be null, if it is we log this as an error!
                if ( action )// We call the action with the return value:
                    action(data[EVENT_PAYLOAD][METHOD_RETURNS]);
                else
                    console.log("No action for method: " + data[EVENT_PAYLOAD][METHOD_NAME]);
            }
        } else if ( data[EVENT_TYPE] === ERROR ) {
            console.log("Server error: " + data[EVENT_PAYLOAD]);
        }
    }
}
