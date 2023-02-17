import {JSONWebSocket} from "./JSONWebSocket";
import {Constants} from "./Constants";
import {ViewModel} from "./ViewModel";
import {Cache} from "./Cache";
import {Session} from "./Session";

const INSTANCES: { [key: string]: Backend } = {};

/**
 *  A frontend representation of the backend.
 *  This abstracts away the details of the communication protocol with the backend,
 *  and it allows you to load view models from the backend and get their frontend representation.
 */
export class Backend
{
  /**
   * This method returns the backend instance for the given server address.
   * If a backend instance for the given server address does not exist yet, it will be created.
   * @param serverAddress the address of the web-socket server to connect to
   */
  static at(serverAddress: string | URL) {
        if (!INSTANCES[serverAddress.toString()])
            INSTANCES[serverAddress.toString()] = new Backend(serverAddress);
        return INSTANCES[serverAddress.toString()];
    }

  /*
      The last part of the API for doing MVVM binding is the entry point
      to a web socket server connection.
      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  */

  private readonly ws : JSONWebSocket;
  private readonly session : Session;
  private readonly cache : Cache;
  private currentViewModelId : string = "";

  /**
   *  This is the entrypoint for the MVVM binding.
   *  Here you can connect to a websocket.
   *
   * @param serverAddress the address of the web-socket server to connect to
   */
  private constructor(serverAddress: string | URL) {
    this.cache = Cache.of(serverAddress.toString());
    this.ws = new JSONWebSocket(serverAddress);
    this.session = new Session(this.ws, this.cache);
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
      frontend: (session: Session, contentVM: ViewModel | any) => void,
  ) {
    // We check if we are already connected to the requested view model:
    if ( this.currentViewModelId === iniViewModelId ) return;
    this.currentViewModelId = iniViewModelId;
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
    frontend: (session: Session, contentVM: ViewModel | any) => void
  ) {
    console.log("Received data: " + JSON.stringify(data));
    // Now let's check the EventType: either a view model or a property change...
    if (data[Constants.EVENT_TYPE] === Constants.RETURN_GET_VM) {
      // We have a view model, so we can set it as the current view model:
      const viewModel = data[Constants.EVENT_PAYLOAD];
      const vmId = viewModel[Constants.VM_ID];

      const vm = new ViewModel(vmId, this.session, viewModel);

      if (this.cache.viewModelObservers[vmId]) {
        this.cache.viewModelObservers[vmId](vm);
        return;
      }
      frontend(this.session, vm);
    } else if (data[Constants.EVENT_TYPE] === Constants.RETURN_PROP) {
      let key = data[Constants.EVENT_PAYLOAD][Constants.VM_ID] + ':' + data[Constants.EVENT_PAYLOAD][Constants.PROP_NAME];
      // We look up the binding for the property change:
      const action = this.cache.propertyObservers[key];
      // If we have a binding, we call it with the new value:
      if (action) action(data[Constants.EVENT_PAYLOAD]);
      else
        console.warn(
            'Backend wants to update property "' + data[Constants.EVENT_PAYLOAD][Constants.PROP_NAME] + '", ' +
            'but no action for property observation event "' + JSON.stringify(data) + '" was found\n' +
            'using key "' + key + '" \n' +
            'Could it be that the current view does not use this property? If so, and this is intended, you can ignore this warning.',
        );
    } else if (data[Constants.EVENT_TYPE] === Constants.CALL_RETURN) {
      const actions =
          this.cache.methodObservers[
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
    }
    else if (data[Constants.EVENT_TYPE] === Constants.ERROR)
      console.error('Server error: ' + JSON.stringify(data[Constants.EVENT_PAYLOAD]));
    else
      console.error(
          'Unknown event type: ' +
          data[Constants.EVENT_TYPE] +
          '! \nData:\n' +
          JSON.stringify(data),
      );
  }
}
