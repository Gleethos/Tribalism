export class Val
{
    getOnceFun;
    onShowFun;
    typeObs;
    getFun;

    constructor(
        get: (arg0: (arg0: any) => void) => void,
        observe: (arg0: (arg0: any) => void) => void,
        type: (arg0: (arg0: any) => void) => void,
    ) {
        this.getOnceFun = get;
        this.onShowFun = observe;
        this.typeObs = type;
        this.getFun = (consumer: any) => {
            get(consumer);
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