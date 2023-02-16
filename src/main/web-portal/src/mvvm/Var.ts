import {Val} from "./Val";
import {Session} from "./Session";
import {Constants} from "./Constants";
import {ViewModel} from "./ViewModel";

/**
 *  A mutable property wrapping an observable item.
 */
export class Var extends Val
{
    constructor(
        session: Session,
        methodName: string,
        vm: ViewModel,
        get: (arg0: (arg0: any) => void) => void,
        observe: (arg0: (arg0: any) => void) => void
    ) {
        super(session, methodName, vm, get, observe);
    }

    set(newItem: any) {
        this.vm.vmCall(
            this.methodName,
            [],
            (property: { [x: string]: any }) => {
                this.vm.vmPropSet(property[Constants.PROP_NAME], newItem);
            },
        );
    }

}