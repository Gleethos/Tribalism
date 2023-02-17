
import {Session} from "./Session";
import {Constants} from "./Constants";
import {ViewModel} from "./ViewModel";

/**
 *  An immutable property wrapping an observable item.
 */
export class Val
{
    protected readonly session: Session
    protected readonly methodName: string;
    protected readonly vm: ViewModel

    private currentItem : any;
    private itemLoaded : boolean = false;
    private state: { [x: string]: any } | null = null; // Once it is loaded, we reuse the value and do not fetch it again!


    constructor(
        session: Session,
        methodName: string,
        vm: ViewModel // The view model that contains the property.
    ) {
        this.session = session;
        this.methodName = methodName;
        this.vm = vm;
    }

    /**
     * Get the raw json representation of the property from the backend or from the cache
     * if it has already been fetched.
     * @param fetcher The listener to be called when the property is fetched.
     */
    protected getState(fetcher: (propAsJson: { [x: string]: any }) =>void) {
        if ( this.state === null ) {
            this.vm.invoke(
                this.methodName,
                [],
                (property: { [x: string]: any }) => {
                    this.state = property;
                    fetcher(property);
                },
            );
        }
        else fetcher(this.state);
    }

    /**
     * Register a listener that will be called when the property is modified in the backend.
     * This will not be triggered when the property is modified in the frontend.
     * @param listener The listener to be called when the property is modified in the backend.
     */
    onShow(listener: (prop: any) => void) {
        this.getState((propAsJson: { [x: string]: any }) => {
                this.vm.observeProperty(propAsJson[Constants.PROP_NAME], (p: { [x: string]: any }) => {
                    // If the property value is a view model, we need to load it:
                    if ( p[Constants.PROP_TYPE][Constants.TYPE_IS_VM] ) {
                        this.session.fetchViewModel(p[Constants.PROP_VALUE], (vm: any) => {
                            // Here we expect a VM object!
                            listener(vm);
                            // The user can call methods on the VM object...
                        });
                    }
                    else listener(p[Constants.PROP_VALUE]); // Here we expect a primitive value!
                });
            });
    }

    /**
     * Get the value of the property, but only once.
     * The supplied listener will be called only once, even if the property is modified in the backend.
     * @param consumer The listener to be called when the property is modified in the backend.
     */
    getOnce(consumer: (item: any) => void) {
        this.getState(
            (propAsJson: { [x: string]: any }) => {
                // If the property value is a view model, we need to load it:
                if (propAsJson[Constants.PROP_TYPE][Constants.TYPE_IS_VM]) {
                    // We expect the property value not to be "undefined":
                    if ( propAsJson[Constants.PROP_VALUE] !== undefined ) {
                        this.session.fetchViewModel(
                            propAsJson[Constants.PROP_VALUE],
                            (vm: ViewModel) => consumer(vm), // Here we expect a VM object where the user can bind to...
                        );
                    } else throw 'Expected a property value, but got undefined!';
                } else consumer(propAsJson[Constants.PROP_VALUE]); // This is a primitive value, we can just pass it on...
            });
    };

    /**
     * Fetch the type of the property.
     *
     * @param consumer The listener to be called when the property type is fetched.
     *                 This listener will be called only once.
     */
    type(consumer: (arg0: any) => void) {
        this.getState(
            (property: { [x: string]: any }) => {
                consumer(property[Constants.PROP_TYPE]);
            });
    }

    /**
     *  This method combines the getOnce and onShow methods.
     *  The supplied listener will be called once, and then again every time the property is modified in the backend.
     * @param listener The listener to be called when the property is modified in the backend.
     */
    get(listener: (arg0: any) => void) {
        if ( this.itemLoaded ) listener(this.currentItem);
        this.getOnce( item => {
            this.currentItem = item;
            listener(item);
        });
        this.onShow(listener);
    }


    toString() {
        return (this.state === null ? '{empty}' : JSON.stringify(this.state));
    }
}