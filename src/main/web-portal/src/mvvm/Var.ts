import {Val} from "./Val";

/**
 *  A mutable property wrapping an observable item.
 */
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

    set(item: any) {
        this.setFun(item);
    }

}