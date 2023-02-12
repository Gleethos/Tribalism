import { useState } from 'react';
import {VM} from "../mvvm/backend-binder";

/**
 * This hook is used to bind a property of a view model to a state hook.
 * It will also update the view model when the state hook changes, and when
 * the view model property changes, then the state hook will be updated as well.
 * @param vm The view model from which a property will be selected.
 * @param propSelector A function that selects the property from the view model.
 * @param defaultValue The default value of the property in the view (If the view model has not yet delivered the "real" value).
 */
export default function useProperty(
  vm: any,
  propSelector: (vm:{
      [x:`${string}`]: {
          (): {
              get: { (arg0: (v: any) => void): void; new (): any };
              set: { (arg0: any): void; new (): any };
          }};
  })=> any,
  defaultValue: string | number | boolean | null | undefined,
) : [any, (v:any) => void] {
  const [state, setState] = useState(defaultValue);

  if (vm) propSelector(vm).get((v: any) => setState(v));
  /*
    So the selector might select something lik this:
    vm.username();
    And then we bind to the state hooks:
    .get( v => setState(v) );
  */
  const setBothStates = (v: any) => {
    setState(v);
    if ( vm ) propSelector(vm).set(v);
  };

  return [state, setBothStates];
}
