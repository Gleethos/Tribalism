import {JSONWebSocket} from "./JSONWebSocket";
import {Cache} from "./Cache";
import {Constants} from "./Constants";
import {ViewModel} from "./ViewModel";

/**
 *  This is a representation of a websocket session.
 *  It allows you to fetch new view models from the server.
 *
 * @param getViewModel a function for fetching a view model from the server
 * @constructor
 */
export class Session {
    private readonly ws: JSONWebSocket;
    readonly cache: Cache;

    constructor(ws: JSONWebSocket, cache: Cache) {
        this.ws = ws;
        this.cache = cache;
    }

    sendVMRequest(vmId: string) {
        if (vmId)
            this.ws.send({[Constants.EVENT_TYPE]: Constants.GET_VM, [Constants.VM_ID]: vmId});
        else
            throw 'The view model id is null!';
    }

    fetchViewModel(vmId: string, action: (vm: ViewModel) => void) {
        // For loading a view model, expects 2 parameters: the view model id and the action to call when the view model is loaded
        if (vmId) {
            // We check if the view model is already cached:
            if (this.cache.viewModelCache[vmId]) {
                action(this.cache.viewModelCache[vmId]);
                return;
            }
            this.cache.viewModelObservers[vmId] = (vm: ViewModel) => {
                this.cache.viewModelCache[vmId] = vm;
                action(vm);
            };
            this.sendVMRequest(vmId);
        } // We log an error if the view model id is null
        else console.error('Expected a view model id, but got null!');
    }
}