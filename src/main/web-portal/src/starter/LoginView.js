import React from 'react';
import useProperty from '../hooks/useProperty.js';

function LoginView({ vm }) {
  const [username, setUsername] = useProperty(vm, (vm) => vm.username(), '');
  const [password, setPassword] = useProperty(vm, (vm) => vm.password(), '');
  const [feedback] = useProperty(vm, (vm) => vm.feedback(), '');
  const [feedbackColor] = useProperty(vm, (vm) => vm.inputValid(), 'black');
  return (
    <div className='row'>
      <h1>Login Survivor!</h1>
      <div className='col-6'>
        <label>User Name</label>
        <input // Now we use the react state
          type='text'
          placeholder='Username'
          value={username}
          onChange={(event) => setUsername(event.target.value)}
        ></input>
      </div>
      <div className='col-6'>
        <label>Password</label>
        <input // Now we use the react state
          type='password'
          placeholder='Password'
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        ></input>
      </div>
      <div className='col-6'>
        <button onClick={() => vm.login()}>Login</button>
      </div>
      <br></br>
      <div className='col-12'>
        <label
          style={{
            color: feedbackColor,
            width: 90 + '%',
            margin: 1 + 'em',
            padding: 1 + 'em',
            border: 1 + 'px solid black',
          }}
          dangerouslySetInnerHTML={{ __html: feedback }}
        ></label>
      </div>
    </div>
  );
}

export default LoginView;
