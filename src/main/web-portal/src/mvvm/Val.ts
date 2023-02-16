
/**
 *  An immutable property wrapping an observable item.
 */
export class Val
{
    private readonly getOnceFun;
    private readonly onShowFun;
    private readonly typeObs;
    private readonly getFun;

    private currentItem : any;
    private itemLoaded : boolean = false;


    constructor(
        get: (arg0: (arg0: any) => void) => void,
        observe: (arg0: (arg0: any) => void) => void,
        type: (arg0: (arg0: any) => void) => void,
    ) {
        this.getOnceFun = get;
        this.onShowFun = observe;
        this.typeObs = type;
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

    type(listener: (arg0: any) => void) {
        return this.typeObs(listener);
    }

    get(listener: (arg0: any) => void) {
        this.getFun(listener);
    }
}