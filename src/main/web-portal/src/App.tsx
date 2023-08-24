import './App.css';
import {Backend} from './mvvm/Backend';
import LoginView from './views/LoginView';
import React, {useState} from 'react';
import {ViewModel} from "./mvvm/ViewModel";
import {Session} from "./mvvm/Session";
import RegisterView from './views/RegisterView';
import FatalErrorView from "./views/FatalErrorView";


const backend = Backend.at('ws://localhost:8080/websocket');
const errorEvents : any[] = [];
backend.onError((event) => { errorEvents.push(event); })

function App() {
  const [content, setContent] = useState<any>(null);
  backend.connectToViewModel(
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
                  if (vm.class === 'app.user.LoginViewModel')
                      setContent(<LoginView vm={vm}/>); // We set the content to the login page
                  else if (vm.class === 'app.user.RegisterViewModel')
                      setContent(<RegisterView vm={vm}/>); // We set the content to the register page
                  else
                      setContent(<FatalErrorView vm={vm} errorEvents={errorEvents}/>); // We set the content to the error page
              });
      },
  );
  if ( content !== null )
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
  // If we don't have content we probably failed to bind to the start page of the MVVM server!
  // So we display an error log;
  return (
    <div className='App relative'>
        <header>
            <title>ERROR!</title>
        </header>
        <div style={{margin:'40px', position: 'absolute', left: '50%', top: '50%', transform: 'translate(-50%, -50%)'}}>
            <h1>ERROR!</h1>
            <div style={{height: '20px'}}>
                <p>
                    <span style={{color: 'red'}}>
                        Failed to bind to the start page of the MVVM server!
                    </span>
                </p>
            </div>
            <div id='main-content' className='App-body'>
                <pre>{errorEvents.map((e) => e.toString()).join('\n')}</pre>
            </div>
        </div>
    </div>
  );
}

export default App;
