import {JSONWebSocket} from "./json-web-socket";
import {Constants} from "./constants";
import {VM} from "./view-model";

/*
    First we define the API for interacting with the MVVM backend:
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */

export class Var {

  getOnceFun;
  onShowFun;
  typeObs;
  getFun;
  setFun;

  constructor(
      get:     (arg0: (arg0: any) => void) => void,
      set:     (newValue: any) => void,
      observe: (arg0: (arg0: any) => void) => void,
      type:    (arg0: (arg0: any) => void) => void,
  ) {
    this.getOnceFun = get;
    this.onShowFun = observe;
    this.typeObs = type;
    this.getFun = (consumer: any) => {
                                      get(consumer);
                                      observe(consumer);
                                    };
    this.setFun = set;
  }
  getOnce(action: (arg0: any) => void) { this.getOnceFun(action); }
  onShow(listener: (arg0: any) => void) { this.onShowFun(listener); }
  type(listener: (arg0: any) => void) { return this.typeObs(listener); }
  set(item: any) { this.setFun(item); }
  get(listener: (arg0: any) => void) { this.getFun(listener); }
}

export class Val {

  getOnceFun;
  onShowFun;
  typeObs;
  getFun;

  constructor(
      get:     (arg0: (arg0: any) => void) => void,
      observe: (arg0: (arg0: any) => void) => void,
      type:    (arg0: (arg0: any) => void) => void,
  ) {
    this.getOnceFun = get;
    this.onShowFun = observe;
    this.typeObs = type;
    this.getFun = (consumer: any) => {
                                      get(consumer);
                                      observe(consumer);
                                    };
  }
  getOnce(action: (arg0: any) => void) { this.getOnceFun(action); }
  onShow(listener: (arg0: any) => void) { this.onShowFun(listener); }
  type(listener: (arg0: any) => void) { return this.typeObs(listener); }
  get(listener: (arg0: any) => void) { this.getFun(listener); }
}

export class Get {
  getFun;
  constructor(get: (consumer: (arg0: any) => void) => void) {
    this.getFun = get;
  }

  get(listener: (arg0: any) => void) { this.getFun(listener) }
}

/**
 *  This is a representation of a websocket session.
 *  It allows you to fetch new view models from the server.
 *
 * @param getViewModel a function for fetching a view model from the server
 * @constructor
 */
export class Session
{
  private ws: JSONWebSocket;

  constructor( ws: JSONWebSocket ) {
    this.ws = ws;
  }

  sendVMRequest(vmId: string) {
    if (vmId) {
      console.info('Requesting view model: ' + vmId);
      this.ws.send({[Constants.EVENT_TYPE]: Constants.GET_VM, [Constants.VM_ID]: vmId});
    } else throw 'The view model id is null!';
  }

  fetchViewModel(vmId: string, action: (vm: VM) => void) {
    // For loading a view model, expects 2 parameters: the view model id and the action to call when the view model is loaded
    if (vmId) {
      // We check if the view model is already cached:
      if (viewModelCache[vmId]) {
        action(viewModelCache[vmId]);
        return;
      }
      else console.info('No cached view model found for id: ' + vmId);
      viewModelObservers[vmId] = (vm: VM) => {
        viewModelCache[vmId] = vm;
        action(vm);
      };
      this.sendVMRequest(vmId);
    } // We log an error if the view model id is null
    else console.error('Expected a view model id, but got null!');
  }
}


const propertyObservers: {[key: string]:(prop: any)=>void} = {};
const viewModelObservers: {[key:string]:(vm: VM)=>void}  = {};
const methodObservers: {[key: string]:any[]} = {};
const viewModelCache: {[key:string]:VM} = {};


export class Backend
{
  /*
      The last part of the API for doing MVVM binding is the entry point
      to a web socket server connection.
      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  */

  private readonly ws : JSONWebSocket;
  private readonly session : Session;

  /**
   *  This is the entrypoint for the MVVM binding.
   *  Here you can connect to a websocket.
   *
   * @param serverAddress the address of the web-socket server to connect to
   */
  constructor(serverAddress: string | URL) {
    this.ws = new JSONWebSocket(serverAddress);
    this.session = new Session(this.ws);

  }

  /**
   *  This is the entrypoint for the MVVM binding.
   *  Here you can connect to a websocket and get a view model
   *  through the second parameter, a function that will receive the above defined
   *  Session object as well as VM object.
   *
   * @param iniViewModelId the id of the view model to load
   * @param frontend the function to call when the view model is loaded
   */

  connectToViewModel(
      iniViewModelId: string,
      frontend: (session: Session, contentVM: VM | any) => void,
  ) {
    this.ws.onReceived(data => this.processResponse(data, frontend));
    this.ws.onConnected(() => this.session.sendVMRequest(iniViewModelId));
  }



  processResponse(data: {
      EventType: string,
      vmId: string,
      EventPayload: {
        [x: string]: any;
        methods: [any],
        vmId: string
      }
    } | {
      [x: string]: {
        [x: string]: any;
        methods: [any]
      }
    },
    frontend: (session: Session, contentVM: VM | any) => void
  ) {
    // Now let's check the EventType: either a view model or a property change...
    if (data[Constants.EVENT_TYPE] === Constants.RETURN_GET_VM) {
      // We have a view model, so we can set it as the current view model:
      const viewModel = data[Constants.EVENT_PAYLOAD];
      const vmId = viewModel[Constants.VM_ID];

      const vm = new VM(
          vmId,
          this.session,
          viewModel,
          this.ws,
          (propName: string, action: (prop: any) => void) => {
            propertyObservers[vmId + ':' + propName] = action;
          },
          (methodName: string, args: any, action: (prop: VM) => void) => {
            let key = vmId + ':' + methodName;
            if (!methodObservers[key]) methodObservers[key] = [];
            methodObservers[key].push(action);
            this.ws.send({
              [Constants.EVENT_TYPE]: Constants.CALL,
              [Constants.EVENT_PAYLOAD]: {
                [Constants.METHOD_NAME]: methodName,
                [Constants.METHOD_ARGS]: args,
              },
              [Constants.VM_ID]: vmId,
            });
          },
      );

      if (viewModelObservers[vmId]) {
        viewModelObservers[vmId](vm);
        return;
      }
      frontend(this.session, vm);
    } else if (data[Constants.EVENT_TYPE] === Constants.RETURN_PROP) {
      // We look up the binding for the property change:
      const action =
          propertyObservers[
          data[Constants.EVENT_PAYLOAD][Constants.VM_ID] + ':' + data[Constants.EVENT_PAYLOAD][Constants.PROP_NAME]
              ];
      // If we have a binding, we call it with the new value:
      if (action) action(data[Constants.EVENT_PAYLOAD]);
      else
        console.error(
            'No action for property observation event: ' + JSON.stringify(data),
        );
    } else if (data[Constants.EVENT_TYPE] === Constants.CALL_RETURN) {
      const actions =
          methodObservers[
          data[Constants.EVENT_PAYLOAD][Constants.VM_ID] + ':' + data[Constants.EVENT_PAYLOAD][Constants.METHOD_NAME]
              ];
      if (actions) {
        // There should at least be one action, if not we log this as an error:
        if (actions.length === 0) {
          console.error(
              'No actions for method: ' + data[Constants.EVENT_PAYLOAD][Constants.METHOD_NAME],
          );
          return;
        }
        // We get and remove the first action from the list:
        let action = actions.shift();

        // The action should not be null, if it is we log this as an error!
        if (action) // We call the action with the return value:
          action(data[Constants.EVENT_PAYLOAD][Constants.METHOD_RETURNS]);
        else
          console.error(
              'No action for method: ' + data[Constants.EVENT_PAYLOAD][Constants.METHOD_NAME],
          );
      }
    } else if (data[Constants.EVENT_TYPE] === Constants.ERROR)
      console.error('Server error: ' + data[Constants.EVENT_PAYLOAD]);
    else
      console.error(
          'Unknown event type: ' +
          data[Constants.EVENT_TYPE] +
          '! \nData:\n' +
          JSON.stringify(data),
      );
  }
}
