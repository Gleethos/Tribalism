import { useState } from 'react';
import {VM} from "../mvvm/view-model";

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
