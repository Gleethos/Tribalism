import logo from './logo.svg';
import './App.css';
import { Var, Val, VM, Session, connect } from "./mvvm/backend-binder";
import LoginView from './starter/LoginView.js';
import * as ReactDOM from 'react-dom';

function App() {
  connect(
    'ws://localhost:8080/websocket',
    'app.ContentViewModel-0', // The "main" view model where the application starts
    (session, contentVM) => {

      console.log("Current view model: " + contentVM);
      // Relevant fields:
      const clazz = contentVM.class;
      const state = contentVM.state;
      const props = state.props; // The properties of the view model
      const methods = state.methods; // The methods of the view model

      // We get the content div, holding the main content
      const content = document.getElementById("main-content");

      contentVM.content().get( vm => {

        console.log("Received content page: " + vm.class);

        // Now let's check if the class is a login page
        if ( vm.class === 'app.LoginViewModel' ) {
            // We set the content from the LoginView
            const loginView = LoginView(vm);
            // This is a react component, so we render it
            ReactDOM.render(loginView, content);
        } else if ( vm.class === 'app.RegisterViewModel' ) {
            // TODO: Implement the register page
            // We make the main page empty:
            content.innerHTML = "<div>REGISTER PAGE</div>";
        }
      })

      // If the user does not want to login, we can switch to the register page using the switch button
      const switchButton = document.getElementById("switch-id");
      switchButton.onclick = () => contentVM.showRegister();
    });
    return (
        <div className="App">
            <header className="App-header">
                <title>Tribee Login!</title>
                <button id="switch-id">Switch</button>
            </header>
            <body>
            <div id="main-content" className="container-fluid">

            </div>
            </body>
        </div>
    );
}

export default App;
