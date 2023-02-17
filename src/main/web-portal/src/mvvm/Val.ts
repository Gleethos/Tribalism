
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
    private readonly getFun;

    private currentItem : any;
    private itemLoaded : boolean = false;


    constructor(
        session: Session,
        methodName: string,
        vm: ViewModel
    ) {
        this.session = session;
        this.methodName = methodName;
        this.vm = vm;
        this.getFun = (consumer: any) => {
            if ( this.itemLoaded ) consumer(this.currentItem);
            this.getOnce( item => {
                this.currentItem = item;
                consumer(item);
            });
            this.onShow(consumer);
        };
    }

    onShow(consumer: (prop: any) => void) {
        this.vm.invoke(
            this.methodName,
            [],
            (property: { [x: string]: any }) => {
                this.vm.observeProperty(property[Constants.PROP_NAME], (p: { [x: string]: any }) => {
                    // If the property value is a view model, we need to load it:
                    if ( p[Constants.PROP_TYPE][Constants.TYPE_IS_VM] ) {
                        this.session.fetchViewModel(p[Constants.PROP_VALUE], (vm: any) => {
                            // Here we expect a VM object!
                            consumer(vm);
                            // The user can call methods on the VM object...
                        });
                    }
                    else consumer(p[Constants.PROP_VALUE]); // Here we expect a primitive value!
                });
            },
        );
    }

    getOnce(consumer: (item: any) => void) {
        this.vm.invoke(
            this.methodName,
            [],
            (property: { [x: string]: any }) => {
                // If the property value is a view model, we need to load it:
                if (property[Constants.PROP_TYPE][Constants.TYPE_IS_VM]) {
                    // We expect the property value not to be "undefined":
                    if ( property[Constants.PROP_VALUE] !== undefined ) {
                        this.session.fetchViewModel(
                            property[Constants.PROP_VALUE],
                            (vm: ViewModel) => consumer(vm), // Here we expect a VM object where the user can bind to...
                        );
                    } else throw 'Expected a property value, but got undefined!';
                } else consumer(property[Constants.PROP_VALUE]); // This is a primitive value, we can just pass it on...
            },
        );
    };

    type(consumer: (arg0: any) => void) {
        this.vm.invoke(
            this.methodName,
            [],
            (property: { [x: string]: any }) => {
                consumer(property[Constants.PROP_TYPE]);
            },
        );
    }

    get(listener: (arg0: any) => void) {
        this.getFun(listener);
    }
}