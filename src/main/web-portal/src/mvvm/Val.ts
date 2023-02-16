
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
    private readonly getOnceFun;
    private readonly onShowFun;
    private readonly getFun;

    private currentItem : any;
    private itemLoaded : boolean = false;


    constructor(
        session: Session,
        methodName: string,
        vm: ViewModel,
        get: (arg0: (arg0: any) => void) => void,
        observe: (arg0: (arg0: any) => void) => void,
    ) {
        this.session = session;
        this.methodName = methodName;
        this.vm = vm;
        this.getOnceFun = get;
        this.onShowFun = observe;
        this.getFun = (consumer: any) => {
            if ( this.itemLoaded ) consumer(this.currentItem);
            get( item => {
                this.currentItem = item;
                consumer(item);
            });
            observe(consumer);
        };
    }

    getOnce(action: (arg0: any) => void) {
        this.getOnceFun(action);
    }

    onShow(listener: (arg0: any) => void) {
        this.onShowFun(listener);
    }

    type(consumer: (arg0: any) => void) {
        this.vm.vmCall(
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