import './App.css';
import { Var, Val, VM, Session, connect } from './mvvm/backend-binder';
import LoginView from './views/LoginView';
import * as ReactDOM from 'react-dom';
import React, { ReactElement, useState } from 'react';
import RegisterView from './views/RegisterView';

function App() {
  const [content, setContent] = useState<any>(null);
  connect(
    'ws://localhost:8080/websocket',
    'app.ContentViewModel-0', // The "main" view model where the application starts
    (session: Session, contentVM: VM | any) => {
      console.log('Current view model: ' + contentVM);
      // Relevant fields:
      const clazz = contentVM.class;
      const state = contentVM.state;
      const props = state.props; // The properties of the view model
      const methods = state.methods; // The methods of the view model

      // We check if the content is missing
      if (content === null)
        contentVM.content().get((vm: { class: string }) => {
          console.log('Received content page: ' + vm.class);

          // Now let's check if the class is a login page
          if (vm.class === 'app.LoginViewModel') {
            // We set the content from the LoginView
            // ignore

            setContent(<LoginView vm={vm} />);
          } else if (vm.class === 'app.RegisterViewModel') {
            // TODO: Implement the register page
            // We make the main page empty:

            setContent(<RegisterView vm={vm} />);
          }
        });

      // If the user does not want to login, we can switch to the register page using the switch button
      const switchButton = document.getElementById('switch-id');
      //switchButton.onclick = () => contentVM.showRegister();
    },
  );
  return (
    <div className='App relative'>
      <header>
        <title>Tribee Login!</title>
      </header>

      <div id='main-content' className='App-body'>
        {content}
      </div>
    </div>
  );
}

export default App;
