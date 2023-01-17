import logo from './logo.svg';
import './App.css';
import './mvvm/app.js';
import { Var, Val, Get, VM, Session, start } from "./mvvm/backend-binder";

function App() {
  const build = event => {
    start(
        'ws://localhost:8080/websocket',
        'app.UserRegistrationViewModel-0', // The "main" view model where the application starts
        (session, vm) => {
          const props = vm.state["props"];
          // We turn the view model json into a pretty string
          const viewModelPrettyString = JSON.stringify(props, null, 4);
          console.log("loadView... "+viewModelPrettyString);
          // Now let's load the view model into the view
          // We attach a simple username password form with a terms of service checkbox
          const username = document.getElementById("username-id");
          //username.value = props.username.value;
          vm.username().get( v => username.value = v );
          username.onkeyup = (event) => vm.username().set(event.target.value);

          const password = document.getElementById("password-id");
          vm.password().get( v => password.value = v );
          password.onkeyup = (event) => { vm.password().set(event.target.value); };

          const email = document.getElementById("email-id");
          email.onkeyup = (event) => { vm.email().set( event.target.value); };
          vm.email().get( v => email.value = v );

          // now the gender drop down:
          const gender = document.getElementById("gender-id");
          vm.gender().type( type => {
            const genders = type.states;
            for (let i = 0; i < genders.length; i++) {
              const option = document.createElement("option");
              option.value = genders[i];
              option.text = genders[i];
              gender.appendChild(option);
            }
          })
          gender.onchange = (event) => vm.gender().set(event.target.value);
          vm.gender().get( v => gender.value = v )

          const termsOfService = document.getElementById("terms-id");
          termsOfService.onchange = (event) => { vm.termsAccepted().set(event.target.checked); };
          vm.termsAccepted().get( v => termsOfService.checked = v );

          const feedback = document.getElementById("feedback-id");
          // We replace \n with br tags
          vm.feedback().get( v => feedback.innerHTML = v.replace(/\\n/g, "<br>") );
          vm.feedbackColor().get( v => feedback.style.color = v );

          const register = document.getElementById("register-id");
          register.onclick = () => vm.register();

          const userPage = document.getElementById("user-page-id");
          vm.userPageViewModel().get( userVM => {
            if ( userVM ) {
              userPage.innerHTML = "<br><label>Edit your user data:</label><br>";
              console.log("userPageViewModel: " + userVM);
              // We want to show location, website link, bio and a save button
              const location = document.createElement("input");
              location.type = "text";
              userVM.location().get(v => location.value = v);
              location.onkeyup = (event) => userVM.location().set(event.target.value);
              userPage.appendChild(location);
              const website = document.createElement("input");
              website.type = "text";
              userVM.website().get(v => website.value = v);
              website.onkeyup = (event) => userVM.website().set(event.target.value);
              userPage.appendChild(website);
              // We add a break
              userPage.appendChild(document.createElement("br"));
              const bio = document.createElement("textarea");
              userVM.bio().get(v => bio.value = v);
              bio.onkeyup = (event) => userVM.bio().set(event.target.value);
              userPage.appendChild(bio);
              const save = document.createElement("button");
              save.innerHTML = "Save";
              save.onclick = () => userVM.save();
              // And finally the feedback of the user view model:
              const userFeedback = document.createElement("label");
              userVM.feedback().get(v => userFeedback.innerHTML = v.replace(/\\n/g, "<br>"));
              //userVM.feedbackColor().get( v => userFeedback.style.color = v );
              userPage.appendChild(userFeedback);
            }
            else
              userPage.innerHTML = "";
          });
        });
  };

  build();

  return (
    <div className="App">
      <header className="App-header">
        <title>Hello WebSocket</title>
        <link href="/webjars/bootstrap/css/bootstrap.min.css" rel="stylesheet"></link>
        <script src="/webjars/jquery/jquery.min.js"></script>
        <script src="/webjars/sockjs-client/sockjs.min.js"></script>
        <script src="/webjars/stomp-websocket/stomp.min.js"></script>
        <img src={logo} className="App-logo" alt="logo" />
        <p>
          Edit <code>src/App.js</code> and save to reload.
        </p>
        <a
          className="App-link"
          href="https://reactjs.org"
          target="_blank"
          rel="noopener noreferrer"
        >
          Learn React
        </a>
      </header>
      <body>
      <div id="main-content" className="container-fluid">
        <div className="row">
          <h1>Registration</h1>
          <div className="col-6">
            <label htmlFor="username-id">User</label><input id="username-id" type="text" placeholder="Username"></input>
          </div>
          <div className="col-6">
            <label htmlFor="password-id">Password</label><input id="password-id" type="password" placeholder="Password"></input>
          </div>
          <div className="col-6">
            <label htmlFor="email-id">Email</label><input id="email-id" type="email" placeholder="Email"></input>
          </div>
          <div className="col-6">
            <label htmlFor="gender-id">Gender</label><select id="gender-id"></select>
          </div>
          <div className="col-6">
            <label htmlFor="terms-id">I agree to the terms of service:</label><input id="terms-id" type="checkbox"></input>
          </div>
          <div className="col-6">
            <button id="register-id">Register</button>
          </div>
          <div className="col-12">
            <label id="feedback-id" style={{width:90+'%', margin: 1+'em', padding:1+'em', border: 1+'px solid black'}}>?</label>
          </div>
        </div>
        <br></br>
          <div className="row">
            <div id="user-page-id" className="col-12"></div>
          </div>
      </div>
      </body>
    </div>
  );
}

export default App;
