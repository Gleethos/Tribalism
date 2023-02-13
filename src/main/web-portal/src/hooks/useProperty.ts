import {useState} from 'react';
import {Var} from "../mvvm/Var";
import {Val} from "../mvvm/Val";

/**
 * This hook is used to bind a property of a view model to a state hook.
 * It will also update the view model when the state hook changes, and when
 * the view model property changes, then the state hook will be updated as well.
 * @param vm The view model from which a property will be selected.
 * @param varSelector A function that selects the property from the view model.
 * @param defaultItem The default value of the property in the view (If the view model has not yet delivered the "real" value).
 */
export function useVar(
  vm: any,
  varSelector: (vm:{ [x:`${string}`]: { (): Var }; })=> Var,
  defaultItem: string | number | boolean | null | undefined,
) : [any, (v:any) => void] {
  const [state, setState] = useState(defaultItem);

  if ( vm ) varSelector(vm).get((v: any) => setState(v));
  /*
    So the selector might select something lik this:
    vm.username();
    And then we bind to the state hooks:
    .get( v => setState(v) );
  */
  const setBothStates = (v: any) => {
    setState(v);
    if ( vm ) varSelector(vm).set(v);
  };

  return [state, setBothStates];
}

export function useVal(
    vm: any,
    valSelector: (vm:{ [x:`${string}`]: { (): Val }; })=> Val,
    defaultValue: string | number | boolean | null | undefined,
) : [any] {
  const [state, setState] = useState(defaultValue);

  if ( vm ) valSelector(vm).get((v: any) => setState(v));
  /*
    So the selector might select something lik this:
    vm.username();
    And then we bind to the state hooks:
    .get( v => setState(v) );
  */
  return [state];
}
