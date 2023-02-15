import './App.css';
import {Backend} from './mvvm/Backend';
import LoginView from './views/LoginView';
import React, {useState} from 'react';
import {ViewModel} from "./mvvm/ViewModel";
import {Session} from "./mvvm/Session";
import RegisterView from './views/RegisterView';

function App() {
  const [content, setContent] = useState<any>(null);
  new Backend('ws://localhost:8080/websocket').connectToViewModel(
    'app.ContentViewModel-0', // The "main" view model where the application starts
    (session: Session, contentVM: ViewModel | any) => {

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
          if (vm.class === 'app.LoginViewModel')
            setContent(<LoginView vm={vm} />); // We set the content to the login page
          else if (vm.class === 'app.RegisterViewModel')
            setContent(<RegisterView vm={vm} />); // We set the content to the register page
        });
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
