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
        vm: ViewModel
    ) {
        super(session, methodName, vm);
    }

    set(newItem: any) {
        this.vm.invoke(
            this.methodName,
            [],
            (property: { [x: string]: any }) => {
                this.vm.vmPropSet(property[Constants.PROP_NAME], newItem);
            },
        );
    }

}