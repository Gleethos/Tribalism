export class Var {

    getOnceFun;
    onShowFun;
    typeObs;
    getFun;
    setFun;

    constructor(
        get: (arg0: (arg0: any) => void) => void,
        set: (newValue: any) => void,
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
        this.setFun = set;
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

    set(item: any) {
        this.setFun(item);
    }

    get(listener: (arg0: any) => void) {
        this.getFun(listener);
    }
}