import React, { useState } from "react";
import ReactDOM from 'react-dom';
import logo from "../logo.svg";

function LoginView({vm}) {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [feedback, setFeedback] = useState("");
    const [feedbackColor, setFeedbackColor] = useState("black");

    console.log("LoginView: " + vm);
    if ( vm ) {
        vm.username().get( v => setUsername(v) );
        vm.password().get( v => setPassword(v) );
        // We replace \n with br tags
        vm.feedback().get( v => setFeedback(v.replace(/\\n/g, "<br>")) );
        vm.inputValid().get( v => setFeedbackColor( v ? "green" : "red" ) );
    }
    return (<div className="row">
                <h1>Login Survivor!</h1>
                <div className="col-6">
                    <label>User Name</label>
                    <input // Now we use the react state
                        type="text"
                        placeholder="Username"
                        value={username}
                        onkeyup={event => vm.username().set(event.target.value)}
                    >
                    </input>
                </div>
                <div className="col-6">
                    <label>Password</label>
                    <input // Now we use the react state
                        type="password"
                        placeholder="Password"
                        value={password}
                        onkeyup={event => vm.password().set(event.target.value)}
                    >
                    </input>
                </div>
                <div className="col-6">
                    <button onClick={() => vm.login()}>Login</button>
                </div>
                <br></br>
                <div className="col-12">
                    <label
                        style={{color: feedbackColor, width:90+'%', margin: 1+'em', padding:1+'em', border: 1+'px solid black'}}
                        dangerouslySetInnerHTML={{__html: feedback}}
                    >
                    </label>
                </div>
                <div>
                    <img src={logo} className="App-logo" alt="logo" />
                </div>
            </div>);
}

export default LoginView;