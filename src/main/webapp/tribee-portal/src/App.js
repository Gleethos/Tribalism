import logo from './logo.svg';
import './App.css';
import './mvvm/app.js';

function App() {
  const connectHandle = event => {
    event.preventDefault()
    console.log('You clicked the button')
    //connect();
  }
  const disconnectHandle = event => {
    event.preventDefault()
    console.log('You clicked the button')
    //disconnect();
  }
  const sendHandle = event => {
    event.preventDefault()
    console.log('You clicked the button')
    //sendName();
  }
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
        <br></br>
        <div id="main-content" className="container">
          <div className="row">
            <div className="col-md-6">
              <form className="form-inline">
                <div className="form-group">
                  <label htmlFor="connect">WebSocket connection:</label>
                  <button onClick={connectHandle} id="connect" className="btn btn-default" type="submit">Connect</button>
                  <button onClick={disconnectHandle} id="disconnect" className="btn btn-default" type="submit" disabled="disabled">Disconnect
                  </button>
                </div>
              </form>
            </div>
            <div className="col-md-6">
              <form className="form-inline">
                <div className="form-group">
                  <label htmlFor="name">What is your name?</label>
                  <input type="text" id="name" className="form-control" placeholder="Your name here..."></input>
                </div>
                <button onClick={sendHandle} id="send" className="btn btn-default" type="submit">Send</button>
              </form>
            </div>
          </div>
          <div className="row">
            <div className="col-md-12">
              <table id="conversation" className="table table-striped">
                <thead>
                <tr>
                  <th>Greetings</th>
                </tr>
                </thead>
                <tbody id="greetings">
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </header>
    </div>
  );
}

export default App;
