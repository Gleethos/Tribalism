import {useState} from 'react';
import {Var} from "../mvvm/Var";
import {Val} from "../mvvm/Val";

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
