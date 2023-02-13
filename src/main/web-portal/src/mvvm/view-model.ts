import {Constants} from "./constants";
import {attachMagic} from "./magic";
import {Get, Session, Val, Var} from "./backend-binder";
import {JSONWebSocket} from "./json-web-socket";

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
export class VM {

    class: string;
    state: { [x: string]: any };

    constructor(
        vmId: string,
        session: Session, // For loading view models like this one
        vmMetaData: { [x: string]: any; methods: [any] }, // The current view model
        ws: JSONWebSocket,
        vmPropObserve: (propName: any, action: (prop: any) => void) => void, // For binding to properties, expects 2 parameters: the property name and the action to call when the property changes
        vmCall: {
            (methodName: any, args: any, action: any): void;
            (
                arg0: any,
                arg1: any[],
                arg2: {
                    (): void;
                    (property: any): void;
                    (property: any): void;
                    (property: any): void;
                    (property: any): void;
                    (property: any): void;
                },
            ): void;
        }, // For calling methods, expects 3 parameters: the method name, the arguments and the action to call when the method returns
    ) {
        // Send a property change to the server, expects 2 arguments: propName, value
        const vmPropSet =
            (propName: string, value: any) => {
                ws.send({
                    [Constants.EVENT_TYPE]: Constants.SET_PROP,
                    [Constants.VM_ID]: vmId,
                    [Constants.PROP_NAME]: propName,
                    [Constants.PROP_VALUE]: value
                });
            };

        this.class = vmMetaData[Constants.CLASS_NAME];
        this.state = vmMetaData;

        // Now we mirror the methods of the Java view model in JS!
        const methods = vmMetaData.methods;
        for (let i = 0; i < methods.length; i++) {
            const method = methods[i];
            // Currently we only support void methods:
            if (method[Constants.METHOD_RETURNS][Constants.TYPE_NAME] === 'void') {
                attachMagic(this, method[Constants.METHOD_NAME], (...args: any) => {
                    vmCall(method[Constants.METHOD_NAME], args, () => {
                    });
                })
            } else if (
                method[Constants.METHOD_RETURNS][Constants.TYPE_NAME] === 'Var' ||
                method[Constants.METHOD_RETURNS][Constants.TYPE_NAME] === 'Val'
            ) {
                attachMagic(this, method[Constants.METHOD_NAME], (...args: any) => {
                    const propGet = (consumer: (item: any) => void) => {
                        vmCall(
                            method[Constants.METHOD_NAME],
                            args,
                            (property: { [x: string]: any }) => {
                                // If the property value is a view model, we need to load it:
                                if (property[Constants.PROP_TYPE][Constants.TYPE_IS_VM]) {
                                    // We expect the property value not to be "undefined":
                                    if (property[Constants.PROP_VALUE] !== undefined) {
                                        session.fetchViewModel(
                                            property[Constants.PROP_VALUE],
                                            (vm: VM) => consumer(vm), // Here we expect a VM object where the user can bind to...
                                        );
                                    } else throw 'Expected a property value, but got undefined!';
                                } else consumer(property[Constants.PROP_VALUE]); // This is a primitive value, we can just pass it on...
                            },
                        );
                    };

                    const propObserve = (consumer: (prop: any) => void) => {
                        vmCall(
                            method[Constants.METHOD_NAME],
                            args,
                            (property: { [x: string]: any }) => {
                                vmPropObserve(property[Constants.PROP_NAME], (p: { [x: string]: any }) => {
                                    // If the property value is a view model, we need to load it:
                                    if (p[Constants.PROP_TYPE][Constants.TYPE_IS_VM]) {
                                        session.fetchViewModel(p[Constants.PROP_VALUE], (vm: any) => {
                                            // Here we expect a VM object!
                                            consumer(vm);
                                            // The user can call methods on the VM object...
                                        });
                                    } else consumer(p[Constants.PROP_VALUE]); // Here we expect a primitive value!
                                });
                            },
                        );
                    };

                    const propSet = (newItem: any) => {
                        vmCall(
                            method[Constants.METHOD_NAME],
                            args,
                            (property: { [x: string]: any }) => {
                                vmPropSet(property[Constants.PROP_NAME], newItem);
                            },
                        );
                    };
                    const propType = (consumer: (arg0: any) => void) => {
                        vmCall(
                            method[Constants.METHOD_NAME],
                            args,
                            (property: { [x: string]: any }) => {
                                consumer(property[Constants.PROP_TYPE]);
                            },
                        );
                    };

                    if (method[Constants.METHOD_RETURNS][Constants.TYPE_NAME] === 'Var') {
                        return new Var(propGet, propSet, propObserve, propType);
                    } else {
                        return new Val(propGet, propObserve, propType);
                    }
                });
            } else {
                attachMagic(this, method[Constants.METHOD_NAME], (...args: any) => {
                    return new Get((consumer: (arg0: any) => void) => {
                        vmCall(
                            method[Constants.METHOD_NAME],
                            args,
                            (property: { [x: string]: any }) => {
                                const value = property[Constants.PROP_VALUE];
                                consumer(value);
                            },
                        );
                    });
                });
            }
        }
    }

    toString() {
        return (
            this.class + '["state":{' + JSON.stringify(this.state, null, 4) + '}]'
        );
    }
}