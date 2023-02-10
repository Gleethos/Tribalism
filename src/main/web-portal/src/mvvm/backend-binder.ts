/*
    Some constants needed to communicate with the server:
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */

const EVENT_TYPE = 'EventType';
const EVENT_PAYLOAD = 'EventPayload';

// Event Type Values:
const SET_PROP = 'act';
const RETURN_PROP = 'show';
const GET_VM = 'getVM';
const RETURN_GET_VM = 'viewModel';
const CALL = 'call';
const CALL_RETURN = 'callReturn';
const ERROR = 'error';

// View model properties:
const VM_ID = 'vmId';
const CLASS_NAME = 'className';
const PROPS = 'props';
const METHOD_NAME = 'name';
const METHOD_ARG_NAME = 'name';
const METHOD_ARG_TYPE = 'type';
const TYPE_NAME = 'type';
const TYPE_IS_VM = 'viewable';
const METHOD_ARG_TYPE_IS_VM = 'viewable';
const METHOD_ARGS = 'args';
const METHOD_RETURNS = 'returns';

// Property properties:
const PROP_NAME = 'propName';
const PROP_VALUE = 'value';
const PROP_TYPE = 'type';
const PROP_TYPE_NAME = 'name';
const PROP_TYPE_STATES = 'states';

// Error properties:
const ERROR_MESSAGE = 'message';
const ERROR_STACK_TRACE = 'stackTrace';
const ERROR_TYPE = 'type';

/*
    First we define the API for interacting with the MVVM backend:
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */

export function Var(
  get: { (consumer: any): void; (arg0: any): void },
  set: (newValue: any) => void,
  observe: { (consumer: any): void; (arg0: any): void },
  type: (consumer: any) => void,
) {
  Var.prototype.getOnce = get;
  Var.prototype.onShow = observe;
  Var.prototype.type = type;
  Var.prototype.get = (consumer: any) => {
    get(consumer);
    observe(consumer);
  };
  Var.prototype.set = set;
}

export function Val(
  get: { (consumer: any): void; (arg0: any): void },
  observe: { (consumer: any): void; (arg0: any): void },
  type: (consumer: any) => void,
) {
  Val.prototype.getOnce = get;
  Val.prototype.onShow = observe;
  Val.prototype.type = type;
  Val.prototype.get = (consumer: any) => {
    get(consumer);
    observe(consumer);
  };
}

export function Get(get: (consumer: any) => void) {
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
export function VM(
  this: any,
  session: {
    get: (arg0: any, arg1: { (vm: any): any; (vm: any): void }) => void;
  }, // For loading view models like this one
  vm: { [x: string]: any; methods: any }, // The current view model
  vmSet: { (propName: any, value: any): void; (arg0: any, arg1: any): void }, // Send a property change to the server, expects 2 arguments: propName, value
  vmObserve: {
    (propName: any, action: any): void;
    (arg0: any, arg1: (p: any) => void): void;
  }, // For binding to properties, expects 2 parameters: the property name and the action to call when the property changes
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
  this.class = vm[CLASS_NAME];
  this.state = vm;
  // Now a pretty to string method:
  VM.prototype.toString = () => {
    return (
      this.class + '["state":{' + JSON.stringify(this.state, null, 4) + '}]'
    );
  };

  // Now we mirror the methods of the Java view model in JS!
  const methods = vm.methods;
  for (let i = 0; i < methods.length; i++) {
    const method = methods[i];
    // Currently we only support void methods:
    if (method[METHOD_RETURNS][TYPE_NAME] === 'void') {
      this[method[METHOD_NAME]] = (...args: any) => {
        vmCall(method[METHOD_NAME], args, () => {});
      };
    } else if (
      method[METHOD_RETURNS][TYPE_NAME] === 'Var' ||
      method[METHOD_RETURNS][TYPE_NAME] === 'Val'
    ) {
      this[method[METHOD_NAME]] = (...args: any) => {
        const propGet = (consumer: (arg0: any) => void) => {
          vmCall(
            method[METHOD_NAME],
            args,
            (property: { [x: string]: any }) => {
              console.log('Got property: ' + JSON.stringify(property));
              // If the property value is a view model, we need to load it:
              if (property[PROP_TYPE][TYPE_IS_VM]) {
                // We expect the property value not to be "undefined":
                if (property[PROP_VALUE] !== undefined) {
                  session.get(
                    property[PROP_VALUE],
                    (vm: any) => consumer(vm), // Here we expect a VM object where the user can bind to...
                  );
                } else throw 'Expected a property value, but got undefined!';
              } else consumer(property[PROP_VALUE]); // This is a primitive value, we can just pass it on...
            },
          );
        };

        const propObserve = (consumer: (arg0: any) => void) => {
          vmCall(
            method[METHOD_NAME],
            args,
            (property: { [x: string]: any }) => {
              vmObserve(property[PROP_NAME], (p: { [x: string]: any }) => {
                // If the property value is a view model, we need to load it:
                if (p[PROP_TYPE][TYPE_IS_VM]) {
                  session.get(p[PROP_VALUE], (vm: any) => {
                    // Here we expect a VM object!
                    consumer(vm);
                    // The user can call methods on the VM object...
                  });
                } else consumer(p[PROP_VALUE]); // Here we expect a primitive value!
              });
            },
          );
        };

        const propSet = (newValue: any) => {
          vmCall(
            method[METHOD_NAME],
            args,
            (property: { [x: string]: any }) => {
              vmSet(property[PROP_NAME], newValue);
            },
          );
        };
        const propType = (consumer: (arg0: any) => void) => {
          vmCall(
            method[METHOD_NAME],
            args,
            (property: { [x: string]: any }) => {
              consumer(property[PROP_TYPE]);
            },
          );
        };

        if (method[METHOD_RETURNS][TYPE_NAME] === 'Var') {
          // how do i get rid of this error?
          return new (Var(propGet, propSet, propObserve, propType) as any)();
        } else {
          return new (Val(propGet, propObserve, propType) as any)();
        }
      };
    } else {
      this[method[METHOD_NAME]] = (...args: any) => {
        return new (Get((consumer: (arg0: any) => void) => {
          vmCall(
            method[METHOD_NAME],
            args,
            (property: { [x: string]: any }) => {
              const value = property[PROP_VALUE];
              consumer(value);
            },
          );
        }) as any)();
      };
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
export function Session(
  getViewModel: (vmId: any, action: any) => void, // For loading a view model, expects 2 parameters: the view model id and the action to call when the view model is loaded
) {
  Session.prototype.get = getViewModel;
}

/*
    The last part of the API for doing MVVM binding is the entry point
    to a web socket server connection.
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
*/

const propertyObservers = {};
const viewModelObservers = {};
const methodObservers = {};
const viewModelCache = {};

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
export function connect(
  serverAddress: string | URL,
  iniViewModelId: string,
  frontend: {
    (session: any, contentVM: any): void;
    (arg0: any, arg1: any): void;
  },
) {
  let ws: WebSocket | null = null;
  const session = new (Session((vmId: string, action: (arg0: any) => void) => {
    if (vmId) {
      // We check if the view model is already cached:
      if (viewModelCache[vmId]) {
        action(viewModelCache[vmId]);
        return;
      } else console.log('No cached view model found for id: ' + vmId);
      viewModelObservers[vmId] = (vm: any) => {
        viewModelCache[vmId] = vm;
        action(vm);
      };
      sendVMRequest(vmId);
    } // We log an error if the view model id is null
    else console.error('Expected a view model id, but got null!');
  }) as any)();

  function startWebsocket(action: { (): void; (): void; (): void; (): void }) {
    ws = new WebSocket(serverAddress);
    ws.onopen = () => {
      action();
    };
    ws.onclose = () => {
      // connection closed, discard old websocket and create a new one in 5s
      ws = null;
      setTimeout(() => startWebsocket(() => {}), 5000);
    };
    ws.onmessage = (event) => {
      //console.log('Message from server: ' + event.data);
      // We parse the data as json:
      processResponse(JSON.parse(event.data));
    };
  }
  startWebsocket(() => sendVMRequest(iniViewModelId));

  function send(data: string) {
    if (data) {
      // First up: If the message is a JSON we turn it into a string:
      const message = typeof data === 'string' ? data : JSON.stringify(data);

      if (ws) {
        // The web socket might be closed, if so we reopen it
        // and send the message when it is open again:
        if (ws.readyState === WebSocket.CLOSED) {
          startWebsocket(() => {
            send(message);
          });
          return;
        }
        console.log('Sending message: ' + message);
        ws.send(message);
      } else {
        console.log(
          "Websocket missing! Failed to send message '" +
            message +
            "'. Retrying in 100ms.",
        );
        // The web socket is not open yet, so we try again in 100ms:
        setTimeout(() => send(message), 100);
      }
    } else throw 'Null is not a valid message!';
  }

  function sendVMRequest(vmId: string) {
    if (vmId) {
      console.log('Requesting view model: ' + vmId);
      send({ [EVENT_TYPE]: GET_VM, [VM_ID]: vmId });
    } else throw 'The view model id is null!';
  }

  function processResponse(data: { [x: string]: string }) {
    // Now let's check the EventType: either a view model or a property change...
    if (data[EVENT_TYPE] === RETURN_GET_VM) {
      // We have a view model, so we can set it as the current view model:
      const viewModel = data[EVENT_PAYLOAD];
      const vmId = viewModel[VM_ID];

      const vm = new VM(
        session,
        viewModel,
        (propName: any, value: any) => {
          send({
            [EVENT_TYPE]: SET_PROP,
            [VM_ID]: vmId,
            [PROP_NAME]: propName,
            [PROP_VALUE]: value,
            charAt: function (pos: number): string {
              throw new Error('Function not implemented.');
            },
            charCodeAt: function (_index: number): number {
              throw new Error('Function not implemented.');
            },
            concat: function (...strings: string[]): string {
              throw new Error('Function not implemented.');
            },
            indexOf: function (
              searchString: string,
              position?: number | undefined,
            ): number {
              throw new Error('Function not implemented.');
            },
            lastIndexOf: function (
              searchString: string,
              position?: number | undefined,
            ): number {
              throw new Error('Function not implemented.');
            },
            localeCompare: function (that: string): number {
              throw new Error('Function not implemented.');
            },
            match: function (regexp: string | RegExp): RegExpMatchArray | null {
              throw new Error('Function not implemented.');
            },
            replace: function (
              searchValue: string | RegExp,
              replaceValue: string,
            ): string {
              throw new Error('Function not implemented.');
            },
            search: function (regexp: string | RegExp): number {
              throw new Error('Function not implemented.');
            },
            slice: function (
              start?: number | undefined,
              end?: number | undefined,
            ): string {
              throw new Error('Function not implemented.');
            },
            split: function (
              separator: string | RegExp,
              limit?: number | undefined,
            ): string[] {
              throw new Error('Function not implemented.');
            },
            substring: function (
              start: number,
              end?: number | undefined,
            ): string {
              throw new Error('Function not implemented.');
            },
            toLowerCase: function (): string {
              throw new Error('Function not implemented.');
            },
            toLocaleLowerCase: function (
              locales?: string | string[] | undefined,
            ): string {
              throw new Error('Function not implemented.');
            },
            toUpperCase: function (): string {
              throw new Error('Function not implemented.');
            },
            toLocaleUpperCase: function (
              locales?: string | string[] | undefined,
            ): string {
              throw new Error('Function not implemented.');
            },
            trim: function (): string {
              throw new Error('Function not implemented.');
            },
            length: 0,
            substr: function (
              from: number,
              length?: number | undefined,
            ): string {
              throw new Error('Function not implemented.');
            },
            codePointAt: function (pos: number): number | undefined {
              throw new Error('Function not implemented.');
            },
            includes: function (
              searchString: string,
              position?: number | undefined,
            ): boolean {
              throw new Error('Function not implemented.');
            },
            endsWith: function (
              searchString: string,
              endPosition?: number | undefined,
            ): boolean {
              throw new Error('Function not implemented.');
            },
            normalize: function (
              form: 'NFC' | 'NFD' | 'NFKC' | 'NFKD',
            ): string {
              throw new Error('Function not implemented.');
            },
            repeat: function (count: number): string {
              throw new Error('Function not implemented.');
            },
            startsWith: function (
              searchString: string,
              position?: number | undefined,
            ): boolean {
              throw new Error('Function not implemented.');
            },
            anchor: function (name: string): string {
              throw new Error('Function not implemented.');
            },
            big: function (): string {
              throw new Error('Function not implemented.');
            },
            blink: function (): string {
              throw new Error('Function not implemented.');
            },
            bold: function (): string {
              throw new Error('Function not implemented.');
            },
            fixed: function (): string {
              throw new Error('Function not implemented.');
            },
            fontcolor: function (color: string): string {
              throw new Error('Function not implemented.');
            },
            fontsize: function (size: number): string {
              throw new Error('Function not implemented.');
            },
            italics: function (): string {
              throw new Error('Function not implemented.');
            },
            link: function (url: string): string {
              throw new Error('Function not implemented.');
            },
            small: function (): string {
              throw new Error('Function not implemented.');
            },
            strike: function (): string {
              throw new Error('Function not implemented.');
            },
            sub: function (): string {
              throw new Error('Function not implemented.');
            },
            sup: function (): string {
              throw new Error('Function not implemented.');
            },
            padStart: function (
              maxLength: number,
              fillString?: string | undefined,
            ): string {
              throw new Error('Function not implemented.');
            },
            padEnd: function (
              maxLength: number,
              fillString?: string | undefined,
            ): string {
              throw new Error('Function not implemented.');
            },
            trimEnd: function (): string {
              throw new Error('Function not implemented.');
            },
            trimStart: function (): string {
              throw new Error('Function not implemented.');
            },
            trimLeft: function (): string {
              throw new Error('Function not implemented.');
            },
            trimRight: function (): string {
              throw new Error('Function not implemented.');
            },
            matchAll: function (
              regexp: RegExp,
            ): IterableIterator<RegExpMatchArray> {
              throw new Error('Function not implemented.');
            },
            [Symbol.iterator]: function (): IterableIterator<string> {
              throw new Error('Function not implemented.');
            },
            at: function (index: number): string | undefined {
              throw new Error('Function not implemented.');
            },
          });
        },
        (propName: string, action: any) => {
          propertyObservers[vmId + ':' + propName] = action;
        },
        (methodName: string, args: any, action: any) => {
          let key = vmId + ':' + methodName;
          if (!methodObservers[key]) methodObservers[key] = [];
          methodObservers[key].push(action);
          send({
            [EVENT_TYPE]: CALL,
            [EVENT_PAYLOAD]: {
              [METHOD_NAME]: methodName,
              [METHOD_ARGS]: args,
            },
            [VM_ID]: vmId,
          });
        },
      );

      if (viewModelObservers[vmId]) {
        viewModelObservers[vmId](vm);
        return;
      }
      frontend(session, vm);
    } else if (data[EVENT_TYPE] === RETURN_PROP) {
      // We look up the binding for the property change:
      const action =
        propertyObservers[
          data[EVENT_PAYLOAD][VM_ID] + ':' + data[EVENT_PAYLOAD][PROP_NAME]
        ];
      // If we have a binding, we call it with the new value:
      if (action) action(data[EVENT_PAYLOAD]);
      else
        console.log(
          'No action for property observation event: ' + JSON.stringify(data),
        );
    } else if (data[EVENT_TYPE] === CALL_RETURN) {
      const actions =
        methodObservers[
          data[EVENT_PAYLOAD][VM_ID] + ':' + data[EVENT_PAYLOAD][METHOD_NAME]
        ];
      if (actions) {
        // There should at least be one action, if not we log this as an error:
        if (actions.length === 0) {
          console.log(
            'No actions for method: ' + data[EVENT_PAYLOAD][METHOD_NAME],
          );
          return;
        }
        // We get and remove the first action from the list:
        let action = actions.shift();

        // The action should not be null, if it is we log this as an error!
        if (action)
          // We call the action with the return value:
          action(data[EVENT_PAYLOAD][METHOD_RETURNS]);
        else
          console.log(
            'No action for method: ' + data[EVENT_PAYLOAD][METHOD_NAME],
          );
      }
    } else if (data[EVENT_TYPE] === ERROR) {
      console.log('Server error: ' + data[EVENT_PAYLOAD]);
    } else {
      console.log(
        'Unknown event type: ' +
          data[EVENT_TYPE] +
          '! \nData:\n' +
          JSON.stringify(data),
      );
    }
  }
}
