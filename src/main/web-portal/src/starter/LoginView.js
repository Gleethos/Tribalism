import React from 'react';
import logo from '../logo.svg';
import useLoginView from '../hooks/useLoginView';

function LoginView({ vm }) {
  const {
    username,
    password,
    feedback,
    feedbackColor,
    setUsername,
    setPassword,
    setFeedback,
    setFeedbackColor,
  } = useLoginView(vm);

  if (vm) {
    vm.username().get((v) => setUsername(v));
    vm.password().get((v) => setPassword(v));
    // We replace \n with br tags
    vm.feedback().get((v) => setFeedback(v.replace(/\\n/g, '<br>')));
    vm.inputValid().get((v) => setFeedbackColor(v ? 'green' : 'red'));
  }

  return (
    <div className='row'>
      <h1>Login Survivor!</h1>
      <div className='col-6'>
        <label>User Name</label>
        <input // Now we use the react state
          type='text'
          placeholder='Username'
          value={username}
          onChange={(event) => vm.username().set(event.target.value)}
        ></input>
      </div>
      <div className='col-6'>
        <label>Password</label>
        <input // Now we use the react state
          type='password'
          placeholder='Password'
          value={password}
          onChange={(event) => vm.password().set(event.target.value)}
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
      <div>
        <img src={logo} className='App-logo' alt='logo' />
      </div>
    </div>
  );
}

export default LoginView;
