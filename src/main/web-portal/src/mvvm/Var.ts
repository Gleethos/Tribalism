import {Val} from "./Val";

export class Var extends Val
{
    setFun;

    constructor(
        get: (arg0: (arg0: any) => void) => void,
        set: (newValue: any) => void,
        observe: (arg0: (arg0: any) => void) => void,
        type: (arg0: (arg0: any) => void) => void,
    ) {
        super(get, observe, type);
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

}