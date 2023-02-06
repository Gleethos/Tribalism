import { useState } from 'react';

export default function useProperty(vm, propSelector, defaultValue) {
  const [state, setState] = useState(defaultValue);

  if (vm) propSelector(vm).get((v) => setState(v));
  /*
    So the selector might select something lik this:
    vm.username();
    And then we bind to the state hooks:
    .get( v => setState(v) );
  */
  const setBothStates = (v) => {
    setState(v);
    if (vm) propSelector(vm).set(v);
  };

  return [state, setBothStates];
}
