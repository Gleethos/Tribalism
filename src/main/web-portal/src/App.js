import logo from './logo.svg';
import './App.css';
import './mvvm/app.js';
import { Var, Val, VM, Session, connect } from "./mvvm/backend-binder";

function App() {
  connect(
    'ws://localhost:8080/websocket',
    'app.ContentViewModel-0', // The "main" view model where the application starts
    (session, vm) => {

      console.log("Current view model: " + vm);
      // Relevant fields:
      const clazz = vm.class;
      const state = vm.state;
      const props = state.props; // The properties of the view model
      const methods = state.methods; // The methods of the view model

      vm.content().get( cvm => {

        console.log("Received content page: " + cvm.class);

        // Now let's check if the class is a login page
        if ( cvm.class === 'app.LoginViewModel' ) {
            // Now let's load the view model into the view
            // We attach a simple username password form with a terms of service checkbox
            const username = document.getElementById("username-id");
            //username.value = props.username.value;
            cvm.username().get( v => username.value = v );
            username.onkeyup = (event) => cvm.username().set(event.target.value);

            const password = document.getElementById("password-id");
            cvm.password().get( v => password.value = v );
            password.onkeyup = (event) => { cvm.password().set(event.target.value); };

            const feedback = document.getElementById("feedback-id");
            // We replace \n with br tags
            cvm.feedback().get( v => feedback.innerHTML = v.replace(/\\n/g, "<br>") );
            cvm.inputValid().get( v => feedback.style.color = v ? "green" : "red" );

            const login = document.getElementById("login-id");
            login.onclick = () => cvm.login();
        } else if ( cvm.class === 'app.RegisterViewModel' ) {
            // TODO: Implement the register page
        }
      })

    });

  return (
    <div className="App">
      <header className="App-header">
        <title>Tribee Login</title>
      </header>
      <body>
      <div id="main-content" className="container-fluid">
        <div className="row">
          <h1>Login</h1>
          <div className="col-6">
            <label htmlFor="username-id">User</label><input id="username-id" type="text" placeholder="Username"></input>
          </div>
          <div className="col-6">
            <label htmlFor="password-id">Password</label><input id="password-id" type="password" placeholder="Password"></input>
          </div>
          <div className="col-6">
            <button id="login-id">Register</button>
          </div>
          <br></br>
          <div className="col-12">
            <label id="feedback-id" style={{width:90+'%', margin: 1+'em', padding:1+'em', border: 1+'px solid black'}}>?</label>
          </div>
        </div>
        <div>
            <img src={logo} className="App-logo" alt="logo" />
        </div>
      </div>
      </body>
    </div>
  );
}

export default App;
