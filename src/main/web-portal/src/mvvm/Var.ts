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

    /**
     * Set the value of the property both in the frontend and in the backend.
     * @param newItem The new value of the property.
     */
    set(newItem: any) {
        this.getState((propAsJson: { [x: string]: any }) => {
                this.vm.vmPropSet(propAsJson[Constants.PROP_NAME], newItem);
            });
    }

}