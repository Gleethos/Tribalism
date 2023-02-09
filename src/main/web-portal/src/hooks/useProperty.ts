import { useState } from 'react';

export default function useProperty(
  vm: any,
  propSelector: {
    (vm: any): any;
    (vm: any): any;
    (vm: any): any;
    (vm: any): any;
    (vm: any): any;
    (vm: any): any;
    (arg0: any): {
      (): any;
      new (): any;
      get: { (arg0: (v: any) => void): void; new (): any };
      set: { (arg0: any): void; new (): any };
    };
  },
  defaultValue: string | number | boolean | null | undefined,
) {
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
    if (vm) propSelector(vm).set(v);
  };

  return [state, setBothStates];
}
