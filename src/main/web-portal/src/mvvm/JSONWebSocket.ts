/**
 * This class is a wrapper around the WebSocket class that allows you to send and receive JSON objects.
 * It also handles reconnection automatically if the connection is lost.
 */
export class JSONWebSocket
{
    private readonly serverAddress: string | URL;
    private ws: WebSocket | null = null;

    // A list of response handlers:
    private responseHandlersList: ((response: {}) => void)[] = [];

    constructor( serverAddress: string | URL ) { this.serverAddress = serverAddress; }

    onConnected(action: () => void) {
        let newWS = new WebSocket(this.serverAddress);
        newWS.onopen = () => {
            action();
        };
        newWS.onclose = () => {
            // connection closed, discard old websocket and create a new one in 5s
            this.ws = null;
            setTimeout(() => this.onConnected(() => {
            }), 5000);
        };
        newWS.onmessage = (event) => {
            // We parse the data as json:
            this.processResponse(JSON.parse(event.data));
        };
        this.ws = newWS;
    }

    private processResponse(response: {}) {
        // We loop through the response handlers and call them all:
        for (let i = 0; i < this.responseHandlersList.length; i++) {
            this.responseHandlersList[i](response);
        }
    }

    onReceived(handler: (response: {}) => void) {
        this.responseHandlersList.push(handler);
    }

    send(data: {} | string) {
        if (data) {
            // First up: If the message is a JSON we turn it into a string:
            const message = typeof data === 'string' ? data : JSON.stringify(data);

            if (this.ws) {
                // The web socket might be closed, if so we reopen it
                // and send the message when it is open again:
                if (this.ws.readyState !== WebSocket.CLOSED) {
                    this.ws.send(message);
                } else
                    this.onConnected(() => {
                        this.send(message);
                    }); // We try to re-establish the connection and send the message again
            } else {
                console.error(
                    "Websocket missing! Failed to send message '" +
                    message +
                    "'. Retrying in 100ms.",
                );
                // The web socket is not open yet, so we try again in 100ms:
                setTimeout(() => this.send(message), 100);
            }
        } else throw 'Null is not a valid message!';
    }

}