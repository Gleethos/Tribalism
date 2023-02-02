import logo from "../logo.svg";

function LoginView(vm) {
    // We do the binding when the page is loaded
    //window.onload = () => {..} // This does not work in react
    // But this:
    setTimeout(() => {
        console.log("LOADED!")
        // Now let's load the view model into the view
        // We attach a simple username password form with a terms of service checkbox
        const username = document.getElementById("username-id");
        //username.value = props.username.value;
        vm.username().get( v => username.value = v );
        username.onkeyup = (event) => vm.username().set(event.target.value);

        const password = document.getElementById("password-id");
        vm.password().get( v => password.value = v );
        password.onkeyup = (event) => { vm.password().set(event.target.value); };

        const feedback = document.getElementById("feedback-id");
        // We replace \n with br tags
        vm.feedback().get( v => feedback.innerHTML = v.replace(/\\n/g, "<br>") );
        vm.inputValid().get( v => feedback.style.color = v ? "green" : "red" );

        const login = document.getElementById("login-id");
        login.onclick = () => vm.login();
    }, 1000);
    return (<div className="row">
                <h1>Login Bro!</h1>
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
                <div>
                    <img src={logo} className="App-logo" alt="logo" />
                </div>
            </div>);
}

export default LoginView;