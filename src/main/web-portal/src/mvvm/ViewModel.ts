import {Constants} from "./Constants";
import {attachMagic} from "./magic";
import {JSONWebSocket} from "./JSONWebSocket";
import {Session} from "./Session";
import {Var} from "./Var";
import {Val} from "./Val";

/**
 *  This is used as a representation of a view model.
 *  it exposes the current state of the view model as
 *  well as a way to bind to its properties in both ways.
 */
export class ViewModel
{
    private readonly session: Session;
    private readonly vmId: string;
    class: string;
    state: { [x: string]: any };

    constructor(
        vmId: string,
        session: Session, // For loading view models like this one
        vmMetaData: { [x: string]: any; methods: [any] } // The current view model
    ) {
        this.session = session;
        this.vmId = vmId;
        this.class = vmMetaData[Constants.CLASS_NAME];
        this.state = vmMetaData;

        // Now we mirror the methods of the Java view model in JS!
        const methods = vmMetaData.methods;
        for ( let i = 0; i < methods.length; i++ ) {
            const method = methods[i];
            // Currently we only support void methods:
            if (method[Constants.METHOD_RETURNS][Constants.TYPE_NAME] === 'void') {
                attachMagic(this, method[Constants.METHOD_NAME], (...args: any) => {
                    this.invoke(method[Constants.METHOD_NAME], args);
                })
            } else if (
                method[Constants.METHOD_RETURNS][Constants.TYPE_NAME] === 'Var' ||
                method[Constants.METHOD_RETURNS][Constants.TYPE_NAME] === 'Val'
            ) {
                attachMagic(this, method[Constants.METHOD_NAME], (...args: any) => {
                    // If there are arguments, we throw an error, because we don't support that yet
                    if (args.length > 0)
                        throw new Error('We don\'t support property returning methods with arguments yet!');

                    if ( method[Constants.METHOD_RETURNS][Constants.TYPE_NAME] === 'Var' )
                        return new Var(session, method[Constants.METHOD_NAME], this);
                    else
                        return new Val(session, method[Constants.METHOD_NAME], this);
                });
            }
            else
                attachMagic(this, method[Constants.METHOD_NAME], (...args: any) => {
                    return new Get((consumer: (arg0: any) => void) => {
                        this.invoke(
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

    // For calling methods, expects 3 parameters: the method name, the arguments and the action to call when the method returns
    invoke(methodName: string, args: any, action: (returnedResult: any) => void = () => {}) {
        let key = this.vmId + ':' + methodName;
        if (!this.session.cache.methodObservers[key]) this.session.cache.methodObservers[key] = [];
        this.session.cache.methodObservers[key].push(action);
        this.session.send({
                [Constants.EVENT_TYPE]: Constants.CALL,
                [Constants.EVENT_PAYLOAD]: {
                    [Constants.METHOD_NAME]: methodName,
                    [Constants.METHOD_ARGS]: args,
                },
                [Constants.VM_ID]: this.vmId,
            }
        );
    }

    // Send a property change to the server, expects 2 arguments: propName, value
    vmPropSet(propName: string, value: any) {
        this.session.send({
            [Constants.EVENT_TYPE]: Constants.SET_PROP,
            [Constants.VM_ID]: this.vmId,
            [Constants.PROP_NAME]: propName,
            [Constants.PROP_VALUE]: value
        });
    }

    // For binding to properties, expects 2 parameters: the property name and the action to call when the property changes
    observeProperty(propName: string, action: (prop: any) => void) {
        let key = this.vmId + ':' + propName;
        this.session.cache.propertyObservers[key] = action;
    };

    toString() {
        return (
            this.class + '["state":{' + JSON.stringify(this.state, null, 4) + '}]'
        );
    }
}


/*
    First we define the API for interacting with the MVVM backend:
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */

class Get {
    getFun;
    constructor(get: (consumer: (arg0: any) => void) => void) {
        this.getFun = get;
    }

    get(listener: (arg0: any) => void) { this.getFun(listener) }
}
