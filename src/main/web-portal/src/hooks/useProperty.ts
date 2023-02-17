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

  const prop = ( vm ? varSelector(vm) : null );
  if ( prop?.isCached() )
    defaultItem = prop?.getCachedItem();

  const [state, setState] = useState(defaultItem);

  if ( prop instanceof Var ) {
    if ( prop.isCached() )
      prop.onShow((v: any) => setState(v));
    else
      prop.get((v: any) => setState(v));
  }

  /*
    So the selector might select something lik this:
    vm.username();
    And then we bind to the state hooks:
    .get( v => setState(v) );
  */
  const setBothStates = (v: any) => {
    setState(v);
    if ( prop instanceof Var )
      prop.set(v);
  };

  return [state, setBothStates];
}

export function useVal(
    vm: any,
    valSelector: (vm:{ [x:`${string}`]: { (): Val }; })=> Val,
    defaultItem: string | number | boolean | null | undefined,
) : [any] {
  const prop = ( vm ? valSelector(vm) : null );
  if ( prop?.isCached() )
    defaultItem = prop?.getCachedItem();

  const [state, setState] = useState(defaultItem);

  if ( prop instanceof Val ) {
    if ( prop.isCached() )
      prop.onShow((v: any) => setState(v));
    else
      prop.get((v: any) => setState(v));
  }

  /*
    So the selector might select something lik this:
    vm.username();
    And then we bind to the state hooks:
    .get( v => setState(v) );
  */
  return [state];
}
